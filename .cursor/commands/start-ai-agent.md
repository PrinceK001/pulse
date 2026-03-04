Start the Pulse AI agent locally for development.

1. Change to `pulse_ai/`
2. Check if a virtual environment exists, create one if not
3. Run `pip install -r requirements.txt` if needed
4. Check if `pulse_agent/.env` exists, if not copy from `.env.example` and remind user to set `GOOGLE_API_KEY`
5. Run `./scripts/run_api.sh` to start uvicorn on port 8001
6. Verify health: `curl http://localhost:8001/list-apps`
