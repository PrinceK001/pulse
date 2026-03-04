---
name: add-ai-sub-agent
description: Step-by-step workflow for adding a new sub-agent or tool to the Pulse AI NL-to-SQL system. Use when creating a new ADK agent, tool, registry entry, or extending the AI pipeline in pulse_ai/.
---

# Add AI Sub-Agent or Tool

## Workflow

```
- [ ] Step 1: Define state keys and constants
- [ ] Step 2: Create tool function (if deterministic)
- [ ] Step 3: Create or update registry entries
- [ ] Step 4: Create the sub-agent
- [ ] Step 5: Wire into pipeline
- [ ] Step 6: Test the agent
```

## Step 1: State Keys and Constants

Add to `pulse_ai/pulse_agent/constants/state_keys.py`:
```python
MY_NEW_KEY = "my_new_key"
```

Add error messages to `constants/error_messages.py` if needed.

## Step 2: Create Tool

Location: `pulse_ai/pulse_agent/tools/deterministic/` or `tools/llm/`

```python
from google.adk.tools import FunctionTool
from google.adk.tools.tool_context import ToolContext
from ...constants.state_keys import STATE_KEYS

def my_tool(tool_context: ToolContext) -> dict:
    input_val = tool_context.state.get(STATE_KEYS.INPUT)
    if not input_val:
        return {"status": "failed", "errors": ["Missing input"]}

    result = process(input_val)
    tool_context.state[STATE_KEYS.OUTPUT] = result
    return {"status": "passed", "data": result}

my_function_tool = FunctionTool(my_tool)
```

## Step 3: Registry Entries

If the tool needs metric, table, or filter knowledge, add to appropriate registry in `registries/`:

```python
# registries/metrics_registry.py
METRICS_REGISTRY["my_new_metric"] = {
    "sql": "quantile(0.99)(Duration)",
    "unit": "nanoseconds",
    "description": "99th percentile latency",
}
```

## Step 4: Create Sub-Agent

For deterministic tools, use a wrapping LlmAgent:
```python
from google.adk.agents import LlmAgent

my_agent = LlmAgent(
    model="gemini-2.5-flash",
    name="my_agent",
    description="Does X with the classified data.",
    instruction="Call my_tool immediately.",
    tools=[my_function_tool],
    disallow_transfer_to_parent=True,
    disallow_transfer_to_peers=True,
)
```

## Step 5: Wire Into Pipeline

Add to the appropriate `SequentialAgent` or `ParallelAgent`:
```python
pipeline = SequentialAgent(
    name="my_pipeline",
    sub_agents=[..., my_agent, ...],
)
```

Update `sub_agents/__init__.py` exports.

## Step 6: Test

```bash
cd pulse_ai && ./scripts/run_api.sh
# Test via API: POST http://localhost:8001/run
```
