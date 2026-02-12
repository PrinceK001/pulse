#!/bin/bash

# ============================================================================
# Pulse Observability - Quick Start Script
# Sets up and starts the entire Pulse platform.
# Auto-detects Docker Compose; falls back to Docker CLI if unavailable.
#
# Usage:
#   ./quickstart.sh
# ============================================================================

# Source common library (provides colors, print_*, check_docker, has_compose,
# run_compose, load_env, constants, and all Docker helpers).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

clear

echo -e "${BLUE}"
cat << "EOF"
╔═══════════════════════════════════════════════════════════════╗
║                                                               ║
║   ██████╗ ██╗   ██╗██╗     ███████╗███████╗                   ║
║   ██╔══██╗██║   ██║██║     ██╔════╝██╔════╝                   ║
║   ██████╔╝██║   ██║██║     ███████╗█████╗                     ║
║   ██╔═══╝ ██║   ██║██║     ╚════██║██╔══╝                     ║
║   ██║     ╚██████╔╝███████╗███████║███████╗                   ║
║   ╚═╝      ╚═════╝ ╚══════╝╚══════╝╚══════╝                   ║
║                                                               ║
║              Observability Platform - Quick Start             ║
║                                                               ║
╚═══════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

if has_compose; then
    echo -e "${CYAN}Mode: Docker Compose (${COMPOSE_CMD})${NC}"
else
    echo -e "${CYAN}Mode: Docker CLI (no compose detected)${NC}"
fi
echo ""
echo "This script will:"
echo "  1. Check prerequisites"
echo "  2. Setup environment"
echo "  3. Build Docker images"
echo "  4. Start all services"
echo "  5. Verify deployment"
echo ""
read -p "Press Enter to continue or Ctrl+C to cancel..."

# ============================================================================
# Step 1: Check Prerequisites
# ============================================================================
print_section "Step 1: Checking Prerequisites"

check_docker

# Re-detect compose after possible Docker installation
detect_compose

# Check available disk space
AVAILABLE_SPACE=$(df -h "$ROOT_DIR" | awk 'NR==2 {print $4}' | sed 's/[GgTt]$//')
if command -v bc &> /dev/null && (( $(echo "$AVAILABLE_SPACE < 20" | bc -l 2>/dev/null || echo 0) )); then
    print_warning "Low disk space: ${AVAILABLE_SPACE}GB available (20GB recommended)"
else
    print_success "Disk space available: ${AVAILABLE_SPACE}"
fi

# Check available memory (macOS vs Linux)
if command -v sysctl &> /dev/null; then
    AVAILABLE_MEM=$(sysctl -n hw.memsize 2>/dev/null || echo 0)
    AVAILABLE_MEM_GB=$((AVAILABLE_MEM / 1024 / 1024 / 1024))
elif [ -f /proc/meminfo ]; then
    AVAILABLE_MEM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    AVAILABLE_MEM_GB=$((AVAILABLE_MEM_KB / 1024 / 1024))
else
    AVAILABLE_MEM_GB=0
fi

if [ "$AVAILABLE_MEM_GB" -gt 0 ] && [ "$AVAILABLE_MEM_GB" -lt 8 ]; then
    print_warning "Low memory: ${AVAILABLE_MEM_GB}GB available (8GB recommended)"
elif [ "$AVAILABLE_MEM_GB" -gt 0 ]; then
    print_success "Sufficient memory: ${AVAILABLE_MEM_GB}GB available"
fi

if has_compose; then
    print_success "Docker Compose detected (${COMPOSE_CMD})"
else
    print_info "Docker Compose not found -- using Docker CLI mode"
fi

# ============================================================================
# Step 2: Setup Environment
# ============================================================================
print_section "Step 2: Setting Up Environment"

cd "$DEPLOY_DIR"

if [ ! -f .env ]; then
    print_info "Creating .env file from template..."
    if [ -f .env.example ]; then
        cp .env.example .env
        print_success "Created .env file"
        print_warning "Please review .env file and update with your actual values"
        read -p "Press Enter after reviewing .env file..."
    else
        print_error ".env.example not found"
        exit 1
    fi
else
    print_success ".env file already exists"
fi

if [ ! -d db ]; then
    print_error "Database initialization directory not found"
    exit 1
fi
print_success "Database initialization files found"

if [ ! -f "$ROOT_DIR/backend/ingestion/otel-collector.yaml" ]; then
    print_error "OTEL collector config not found"
    exit 1
fi
print_success "OTEL collector configuration found"

if [ ! -f "$ROOT_DIR/backend/ingestion/clickhouse-otel-schema.sql" ]; then
    print_error "ClickHouse schema not found"
    exit 1
fi
print_success "ClickHouse schema found"

# ============================================================================
# Step 3: Build Docker Images
# ============================================================================
print_section "Step 3: Building Docker Images"

print_info "This may take 15-20 minutes on first run..."
echo ""

if "$SCRIPT_DIR/build.sh"; then
    print_success "All Docker images built successfully"
else
    print_error "Failed to build Docker images"
    echo "Check logs above for details"
    exit 1
fi

# ============================================================================
# Step 4: Start Services
# ============================================================================
print_section "Step 4: Starting Services"

print_info "Starting all services in detached mode..."
echo ""

if "$SCRIPT_DIR/start.sh" -d; then
    print_success "All services started"
else
    print_error "Failed to start services"
    echo "Check logs: ./logs.sh"
    exit 1
fi

# ============================================================================
# Step 5: Verify Deployment
# ============================================================================
print_section "Step 5: Verifying Deployment"

sleep 5

print_info "Checking container status..."
if has_compose; then
    run_compose ps
else
    docker ps --filter "network=$NETWORK_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
fi
echo ""

print_info "Testing backend health endpoint..."
if curl -sf http://localhost:8080/healthcheck > /dev/null 2>&1; then
    print_success "Backend is responding"
else
    print_warning "Backend health check failed (may still be starting)"
fi

print_info "Testing frontend..."
if curl -sf http://localhost:3000/healthcheck.txt > /dev/null 2>&1; then
    print_success "Frontend is responding"
else
    print_warning "Frontend health check failed (may still be starting)"
fi

print_info "Testing MySQL connection..."
if docker exec -T "$CONTAINER_MYSQL" mysql -u pulse_user -ppulse_password pulse_db -e "SELECT 1" > /dev/null 2>&1; then
    print_success "MySQL is accessible"
else
    print_warning "MySQL connection failed"
fi

print_info "Testing ClickHouse connection..."
if curl -s 'http://localhost:8123/?query=SELECT+1' > /dev/null 2>&1; then
    print_success "ClickHouse is accessible"
else
    print_warning "ClickHouse connection failed"
fi

# ============================================================================
# Final Summary
# ============================================================================
print_section "Deployment Complete!"

echo -e "${GREEN}Your Pulse Observability platform is now running!${NC}"
echo ""
echo -e "${CYAN}Access Points:${NC}"
echo -e "  ${BLUE}Frontend (UI):${NC}      http://localhost:3000"
echo -e "  ${BLUE}Backend API:${NC}        http://localhost:8080"
echo -e "  ${BLUE}Health Check:${NC}       http://localhost:8080/healthcheck"
echo -e "  ${BLUE}MySQL:${NC}              localhost:3307"
echo -e "  ${BLUE}ClickHouse:${NC}         localhost:8123 (HTTP), localhost:9000 (Native)"
echo -e "  ${BLUE}OTEL Collector:${NC}     localhost:4317 (gRPC), localhost:4318 (HTTP)"
echo ""
echo -e "${CYAN}Useful Commands:${NC}"
echo -e "  ${BLUE}View all logs:${NC}      ./deploy/scripts/logs.sh"
echo -e "  ${BLUE}View server logs:${NC}   docker logs -f pulse-server"
echo -e "  ${BLUE}Check status:${NC}       docker ps --filter network=pulse-network"
echo -e "  ${BLUE}Stop services:${NC}      ./deploy/scripts/stop.sh"
echo -e "  ${BLUE}Reset databases:${NC}    ./deploy/scripts/reset-databases.sh"
echo ""
echo -e "${CYAN}Next Steps:${NC}"
echo -e "  1. Open http://localhost:3000 in your browser"
echo -e "  2. Sign in with Google OAuth (or use dummy login if OAuth is disabled)"
echo -e "  3. Explore the dashboard and features"
echo ""
echo -e "${YELLOW}Troubleshooting:${NC}"
echo -e "  If you encounter issues:"
echo -e "  1. Check logs: ${BLUE}./deploy/scripts/logs.sh${NC}"
echo -e "  2. Verify services: ${BLUE}docker ps --filter network=pulse-network${NC}"
echo -e "  3. Restart: ${BLUE}./deploy/scripts/stop.sh && ./deploy/scripts/start.sh -d${NC}"
echo ""
echo -e "${GREEN}Happy monitoring!${NC}"
echo ""
