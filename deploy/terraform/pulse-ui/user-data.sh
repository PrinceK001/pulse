#!/bin/bash
set -e  # Exit on error

# Log all output
exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user-data script at $(date)"

# Clone the repository to /opt/bin
echo "Cloning pulse repository to /opt/bin..."
mkdir -p /opt/bin
cd /opt/bin

if [ -d "pulse" ]; then
    rm -rf pulse
fi
git clone https://github.com/dream-horizon-org/pulse.git

#!/usr/bin/env bash
echo "### Running build for dashboard...."

DESIRED_NODE_VERSION="v20.12.2"

echo "### Installing Node.js $DESIRED_NODE_VERSION via nvm..."

if ! command -v curl >/dev/null 2>&1; then
  echo "### ERROR: curl is required to install nvm/Node.js" >&2
  exit 1
fi

export NVM_DIR="$HOME/.nvm"

# Install nvm if not already present
if [ ! -s "$NVM_DIR/nvm.sh" ]; then
  echo "### nvm not found, installing..."
  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
fi

# Load nvm into this shell
# shellcheck source=/dev/null
. "$NVM_DIR/nvm.sh"

# Install and use the desired node version
nvm install "$DESIRED_NODE_VERSION"
nvm use "$DESIRED_NODE_VERSION"


echo "### Using node: $(node -v)"
echo "### Using npm:  $(npm -v)"

sudo apt update && sudo apt install rsync -y

cd pulse/pulse-ui || exit
echo "### Installing yarn..."
npm install -g yarn
yarn --version

echo "### Installing pm2..."
npm install -g pm2

echo "### Listing files..."
ls -la

#######################################

yarn --version

ENV_FILE=".env"
ENV="prod"

touch "$ENV_FILE"
# Use a temporary file or a single sed command for efficiency
%{ for env_var in env_vars ~}
# We use | as a delimiter in case the key contains slashes
# We use \Q \E or escape dots if necessary, but usually, anchors suffice
sed -i '\|^${env_var.key}=|d' "$ENV_FILE" 2>/dev/null || true
%{ endfor ~}


export DEPLOYMENT=production

echo "node version"
node --version

export NODE_OPTIONS=--max_old_space_size=4096
yarn install
NODE_ENV=production PORT=3000 yarn build

echo "staring pulse dashboard........................"
pm2 start "pm2/pm2-$ENV.json" -i 1