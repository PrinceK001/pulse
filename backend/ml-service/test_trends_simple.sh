#!/bin/bash
# Simple test for trends endpoint with both current and historical data

curl -s -X POST http://localhost:8000/analyze/trends \
  -H "Content-Type: application/json" \
  -d '{
    "users": [
      {"user_id": "current_1", "sessions_last_7_days": 0, "sessions_last_30_days": 5, "days_since_last_session": 35, "avg_session_duration": 30000, "unique_screens_last_7_days": 0, "crash_count_last_7_days": 3, "anr_count_last_7_days": 2, "frozen_frame_rate": 0.3},
      {"user_id": "current_2", "sessions_last_7_days": 1, "sessions_last_30_days": 8, "days_since_last_session": 10, "avg_session_duration": 60000, "unique_screens_last_7_days": 2, "crash_count_last_7_days": 2, "anr_count_last_7_days": 1, "frozen_frame_rate": 0.2}
    ]
  }' | python3 -m json.tool

