Start the Pulse Docker services for local development.

1. Change to the `deploy/` directory
2. Check if `.env` exists, if not copy from `.env.example`
3. Run `./scripts/start.sh -d` to start all services in detached mode
4. Wait for health checks to pass on key services:
   - pulse-server: `curl http://localhost:8080/healthcheck`
   - pulse-ui: `curl http://localhost:3000/healthcheck.txt`
   - pulse-ai: `curl http://localhost:8001/list-apps`
5. Report which services are healthy and which failed
