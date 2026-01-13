#!/bin/bash
set -euo pipefail

# Log all output
exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

ROLE="${role}"
NODE_ID="${node_id}"
NUM_CONTROLLERS="${num_controllers}"
NUM_BROKERS="${num_brokers}"
KRAFT_CLUSTER_ID="${kraft_cluster_id}"
KAFKA_DIR="/opt/kafka"
DATA_DIR="${kafka_data_dir}"          # mount point for broker data disk (e.g. /var/lib/kafka)
ZONE_NAME="${route53_zone_name}"

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y nvme-cli util-linux e2fsprogs

# ------------------------------------------------------------
# Controllers: use ROOT disk for metadata logs
# Brokers: mount secondary EBS disk at $DATA_DIR and use $DATA_DIR/data
# ------------------------------------------------------------
if [[ "$ROLE" == "controller" ]]; then
  KAFKA_LOG_DIR="/var/lib/kafka-metadata"
  mkdir -p "$KAFKA_LOG_DIR"
else
  mkdir -p "$DATA_DIR"

  # ----------------------------
  # Find non-root disk (EBS data)
  # ----------------------------
  ROOT_SRC="$(findmnt -n -o SOURCE /)"
  ROOT_DISK="$(lsblk -no PKNAME "$ROOT_SRC" 2>/dev/null || true)"
  if [[ -z "$ROOT_DISK" ]]; then
    ROOT_DISK="$(basename "$ROOT_SRC" | sed 's/p[0-9]\+$//' || true)"
  fi

  DATA_DISK=""
  while read -r name type; do
    [[ "$type" != "disk" ]] && continue
    [[ "$name" == "$ROOT_DISK" ]] && continue
    DATA_DISK="/dev/$name"
    break
  done < <(lsblk -ndo NAME,TYPE)

  if [[ -z "$DATA_DISK" ]]; then
    echo "ERROR: Could not find non-root data disk for broker. lsblk:"
    lsblk
    exit 1
  fi

  # Format disk if needed
  if ! blkid "$DATA_DISK" >/dev/null 2>&1; then
    mkfs.ext4 -F "$DATA_DISK"
  fi

  # Mount + persist
  UUID="$(blkid -s UUID -o value "$DATA_DISK")"
  grep -q "$UUID" /etc/fstab || echo "UUID=$UUID $DATA_DIR ext4 defaults,nofail 0 2" >> /etc/fstab
  mount -a

  # IMPORTANT: log.dirs must not be filesystem root (ext4 creates lost+found)
  KAFKA_LOG_DIR="$DATA_DIR/data"
  mkdir -p "$KAFKA_LOG_DIR"
fi

# ---------------------------------
# Build controller.quorum.voters (id@host:port)
# and controller.quorum.bootstrap.servers (host:port)
# ---------------------------------
QUORUM=""
CONTROLLER_BOOTSTRAP=""
i=1
while [[ $i -le $NUM_CONTROLLERS ]]; do
  IDX="$(printf "%02d" "$i")"
  HOST="pulse-kafka-controller-$IDX.$ZONE_NAME"

  if [[ -z "$QUORUM" ]]; then
    QUORUM="$i@$HOST:9093"
  else
    QUORUM="$QUORUM,$i@$HOST:9093"
  fi

  if [[ -z "$CONTROLLER_BOOTSTRAP" ]]; then
    CONTROLLER_BOOTSTRAP="$HOST:9093"
  else
    CONTROLLER_BOOTSTRAP="$CONTROLLER_BOOTSTRAP,$HOST:9093"
  fi

  i=$((i+1))
done

# ---------------------------------
# Wait for Route53 private DNS to resolve (avoid early startup race)
# ---------------------------------
for j in $(seq 1 60); do
  ok=1
  i=1
  while [[ $i -le $NUM_CONTROLLERS ]]; do
    IDX="$(printf "%02d" "$i")"
    HOST="pulse-kafka-controller-$IDX.$ZONE_NAME"
    getent hosts "$HOST" >/dev/null 2>&1 || ok=0
    i=$((i+1))
  done
  [[ $ok -eq 1 ]] && break
  sleep 2
done

# ---------------------------------
# Stable advertised FQDN (Route53)
# ---------------------------------
if [[ "$ROLE" == "controller" ]]; then
  IDX="$(printf "%02d" "$NODE_ID")"
  ADVERTISED_FQDN="pulse-kafka-controller-$IDX.$ZONE_NAME"
else
  BROKER_INDEX=$(( NODE_ID - NUM_CONTROLLERS ))
  IDX="$(printf "%02d" "$BROKER_INDEX")"
  ADVERTISED_FQDN="pulse-kafka-broker-$IDX.$ZONE_NAME"
fi

CONFIG_DIR="$KAFKA_DIR/config/kraft"
CONF_FILE="$CONFIG_DIR/server.properties"
mkdir -p "$CONFIG_DIR"

# ---------------------------------
# Auto-adjust replication / ISR (based on number of brokers)
# ---------------------------------
RF="$NUM_BROKERS"
if (( RF > 3 )); then RF=3; fi
MIN_ISR=$(( RF - 1 ))
if (( MIN_ISR < 1 )); then MIN_ISR=1; fi

# ---------------------------------
# Write Kafka KRaft config
# ---------------------------------
if [[ "$ROLE" == "controller" ]]; then
cat > "$CONF_FILE" <<EOF
process.roles=controller
node.id=$NODE_ID

controller.quorum.voters=$QUORUM

listener.security.protocol.map=CONTROLLER:PLAINTEXT
controller.listener.names=CONTROLLER
listeners=CONTROLLER://0.0.0.0:9093

log.dirs=$KAFKA_LOG_DIR
EOF
else
cat > "$CONF_FILE" <<EOF
process.roles=broker
node.id=$NODE_ID

controller.quorum.voters=$QUORUM
controller.quorum.bootstrap.servers=$CONTROLLER_BOOTSTRAP

# Required on broker: name used for controller connections
controller.listener.names=CONTROLLER

# Map includes CONTROLLER even though broker doesn't bind it
listener.security.protocol.map=INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
inter.broker.listener.name=INTERNAL

# Broker binds only INTERNAL for client + inter-broker traffic
listeners=INTERNAL://0.0.0.0:9092
advertised.listeners=INTERNAL://$ADVERTISED_FQDN:9092

log.dirs=$KAFKA_LOG_DIR

default.replication.factor=$RF
min.insync.replicas=$MIN_ISR
offsets.topic.replication.factor=$RF
transaction.state.log.replication.factor=$RF
transaction.state.log.min.isr=$MIN_ISR
EOF
fi

# ---------------------------------
# Format storage only once (based on the log dir we actually use)
# ---------------------------------
FORMAT_MARKER="$KAFKA_LOG_DIR/.kraft_formatted"
if [[ ! -f "$FORMAT_MARKER" ]]; then
  "$KAFKA_DIR/bin/kafka-storage.sh" format -t "$KRAFT_CLUSTER_ID" -c "$CONF_FILE"
  touch "$FORMAT_MARKER"
fi

# ---------------------------------
# systemd service
# ---------------------------------
cat > /etc/systemd/system/kafka.service <<SERVICE
[Unit]
Description=Apache Kafka (KRaft) - $ROLE
After=network.target

[Service]
Type=simple
WorkingDirectory=$KAFKA_DIR
ExecStart=$KAFKA_DIR/bin/kafka-server-start.sh $CONF_FILE
ExecStop=$KAFKA_DIR/bin/kafka-server-stop.sh
Restart=on-failure
RestartSec=5
LimitNOFILE=100000

[Install]
WantedBy=multi-user.target
SERVICE

systemctl daemon-reload
systemctl enable kafka
systemctl restart kafka
