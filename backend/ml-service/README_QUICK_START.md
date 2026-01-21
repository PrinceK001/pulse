# Quick Start - One Command Testing

## Run Everything with One Command

```bash
cd backend/ml-service
./run_and_test.sh
```

This single command will:
1. ✅ Check Python installation
2. ✅ Create virtual environment (if needed)
3. ✅ Install all dependencies
4. ✅ Train the ML model (if not exists)
5. ✅ Start the ML service
6. ✅ Run comprehensive tests
7. ✅ Show test results

## What You'll See

The script will:
- Install dependencies automatically
- Train a model with 2000 sample users
- Start the service on port 8000
- Run 5 test scenarios:
  - Health check
  - Medium risk user prediction
  - High risk user prediction
  - Low risk user prediction
  - Batch prediction

## After Running

The service will continue running in the background. You can:

**View logs:**
```bash
tail -f /tmp/ml_service.log
```

**Stop the service:**
```bash
# Find the PID from the script output, then:
kill <PID>
```

**Test manually:**
```bash
curl http://localhost:8000/health
```

**View API documentation:**
Open http://localhost:8000/docs in your browser

## Troubleshooting

**Port 8000 already in use:**
- The script will try to kill the existing process
- Or manually: `lsof -t -i:8000 | xargs kill`

**Python not found:**
- Install Python 3.11+: `brew install python3` (Mac) or `apt install python3` (Linux)

**Permission denied:**
- Make script executable: `chmod +x run_and_test.sh`

## Next: Test Java Integration

Once ML service is running:

```bash
# Set ML service URL
export CONFIG_SERVICE_APPLICATION_MLSERVICEBASEURL=http://localhost:8000

# Start your Java backend and test:
curl -X POST http://localhost:8080/api/v1/churn/predictions \
  -H "Content-Type: application/json" \
  -d '{"limit": 5}'
```

