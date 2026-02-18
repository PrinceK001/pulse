#!/bin/bash

# Pulse Observability - Quick Start Script
# This script sets up and starts the entire Pulse platform

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$DEPLOY_DIR")"

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

echo ""
echo -e "${CYAN}🚀 Welcome to Pulse Observability Quick Start!${NC}"
echo ""
echo "This script will:"
echo "  1. Check prerequisites"
echo "  2. Setup environment"
echo "  3. Build Docker images"
echo "  4. Start all services"
echo "  5. Verify deployment"
echo ""
read -p "Press Enter to continue or Ctrl+C to cancel..."

# Function to print section headers
print_section() {
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}  $1${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

# Function to print success
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Function to print error
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Function to print warning
print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Function to print info
print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Step 1: Check Prerequisites
print_section "Step 1: Checking Prerequisites"

# Check Docker
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed"
    echo "Please install Docker from: https://docs.docker.com/get-docker/"
    exit 1
fi
print_success "Docker is installed ($(docker --version))"

# Check Docker Compose
if ! command -v docker-compose &> /dev/null; then
    print_error "Docker Compose is not installed"
    echo "Please install Docker Compose from: https://docs.docker.com/compose/install/"
    exit 1
fi
print_success "Docker Compose is installed ($(docker-compose --version))"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    print_error "Docker daemon is not running"
    echo "Please start Docker and try again"
    exit 1
fi
print_success "Docker daemon is running"

# Check available disk space
AVAILABLE_SPACE=$(df -h "$ROOT_DIR" | awk 'NR==2 {print $4}' | sed 's/G//')
if (( $(echo "$AVAILABLE_SPACE < 20" | bc -l) )); then
    print_warning "Low disk space: ${AVAILABLE_SPACE}GB available (20GB recommended)"
else
    print_success "Sufficient disk space: ${AVAILABLE_SPACE}GB available"
fi

# Check available memory
AVAILABLE_MEM=$(sysctl -n hw.memsize 2>/dev/null || free -g | awk '/^Mem:/{print $2}')
AVAILABLE_MEM_GB=$((AVAILABLE_MEM / 1024 / 1024 / 1024))
if [ "$AVAILABLE_MEM_GB" -lt 8 ]; then
    print_warning "Low memory: ${AVAILABLE_MEM_GB}GB available (8GB recommended)"
else
    print_success "Sufficient memory: ${AVAILABLE_MEM_GB}GB available"
fi

# Step 2: Setup Environment
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

# Verify required directories
if [ ! -d db ]; then
    print_error "Database initialization directory not found"
    exit 1
fi
print_success "Database initialization files found"

# Step 3: Build Docker Images
print_section "Step 3: Building Docker Images"

print_info "This may take 15-20 minutes on first run..."
echo ""

if docker-compose build --parallel; then
    print_success "All Docker images built successfully"
else
    print_error "Failed to build Docker images"
    echo "Check logs above for details"
    exit 1
fi

# Step 4: Start Services
print_section "Step 4: Starting Services"

print_info "Starting all services in detached mode..."
echo ""

if docker-compose up -d; then
    print_success "All services started"
else
    print_error "Failed to start services"
    echo "Check logs: docker-compose logs -f"
    exit 1
fi

# Wait for services to be healthy
print_info "Waiting for services to become healthy..."
echo ""

TIMEOUT=180  # 3 minutes
ELAPSED=0
INTERVAL=5

while [ $ELAPSED -lt $TIMEOUT ]; do
    MYSQL_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' pulse-mysql 2>/dev/null || echo "starting")
    CLICKHOUSE_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' pulse-clickhouse 2>/dev/null || echo "starting")
    SERVER_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' pulse-server 2>/dev/null || echo "starting")
    UI_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' pulse-ui 2>/dev/null || echo "starting")
    
    echo -ne "\r  MySQL: $MYSQL_HEALTH | ClickHouse: $CLICKHOUSE_HEALTH | Server: $SERVER_HEALTH | UI: $UI_HEALTH"
    
    if [ "$MYSQL_HEALTH" = "healthy" ] && [ "$CLICKHOUSE_HEALTH" = "healthy" ] && [ "$SERVER_HEALTH" = "healthy" ] && [ "$UI_HEALTH" = "healthy" ]; then
        echo ""
        print_success "All services are healthy!"
        break
    fi
    
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
done

echo ""

if [ $ELAPSED -ge $TIMEOUT ]; then
    print_warning "Services are taking longer than expected to become healthy"
    print_info "Check status: docker-compose ps"
    print_info "Check logs: docker-compose logs -f"
fi

# Step 5: Verify Deployment
print_section "Step 5: Verifying Deployment"

# Check container status
print_info "Checking container status..."
docker-compose ps
echo ""

# Test backend health
print_info "Testing backend health endpoint..."
if curl -f http://localhost:8080/healthcheck > /dev/null 2>&1; then
    print_success "Backend is responding"
else
    print_warning "Backend health check failed (may still be starting)"
fi

# Test frontend
print_info "Testing frontend..."
if curl -f http://localhost:3000/healthcheck.txt > /dev/null 2>&1; then
    print_success "Frontend is responding"
else
    print_warning "Frontend health check failed (may still be starting)"
fi

# Test MySQL
print_info "Testing MySQL connection..."
if docker-compose exec -T mysql mysql -u pulse_user -ppulse_password pulse_db -e "SELECT 1" > /dev/null 2>&1; then
    print_success "MySQL is accessible"
else
    print_warning "MySQL connection failed"
fi

# Test ClickHouse
print_info "Testing ClickHouse connection..."
if curl -s 'http://localhost:8123/?query=SELECT+1' > /dev/null 2>&1; then
    print_success "ClickHouse is accessible"
else
    print_warning "ClickHouse connection failed"
fi

# Final Summary
print_section "🎉 Deployment Complete!"

echo -e "${GREEN}Your Pulse Observability platform is now running!${NC}"
echo ""
echo -e "${CYAN}Access Points:${NC}"
echo -e "  ${BLUE}Frontend (UI):${NC}      http://localhost:3000"
echo -e "  ${BLUE}Backend API:${NC}        http://localhost:8080"
echo -e "  ${BLUE}Health Check:${NC}       http://localhost:8080/healthcheck"
echo -e "  ${BLUE}MySQL:${NC}              localhost:3307 (mapped from 3306)"
echo -e "  ${BLUE}ClickHouse:${NC}         localhost:8123 (HTTP), localhost:9000 (Native)"
echo ""
echo -e "${CYAN}Useful Commands:${NC}"
echo -e "  ${BLUE}View all logs:${NC}      docker-compose logs -f"
echo -e "  ${BLUE}View server logs:${NC}   docker-compose logs -f pulse-server"
echo -e "  ${BLUE}View UI logs:${NC}       docker-compose logs -f pulse-ui"
echo -e "  ${BLUE}Check status:${NC}       docker-compose ps"
echo -e "  ${BLUE}Stop services:${NC}      docker-compose down"
echo -e "  ${BLUE}Restart:${NC}            docker-compose restart"
echo ""
echo -e "${CYAN}Next Steps:${NC}"
echo -e "  1. Open http://localhost:3000 in your browser"
echo -e "  2. Sign in with Google OAuth"
echo -e "  3. Explore the dashboard and features"
echo -e "  4. Check the Testing Guide: ${BLUE}deploy/TESTING_GUIDE.md${NC}"
echo ""
echo -e "${YELLOW}Troubleshooting:${NC}"
echo -e "  If you encounter issues:"
echo -e "  1. Check logs: ${BLUE}docker-compose logs -f${NC}"
echo -e "  2. Verify all services are healthy: ${BLUE}docker-compose ps${NC}"
echo -e "  3. Review the Testing Guide for common issues"
echo ""
echo -e "${GREEN}Happy monitoring! 🚀${NC}"
echo ""

