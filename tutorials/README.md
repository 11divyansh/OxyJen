# Oxyjen Tutorials

This folder contains topic-wise code tutorials for Oxyjen.

Structure:
- `easy/` for first steps and core primitives.
- `modes/` for runtime, collection, schema, and sandbox behavior.
- `loops/` for repeat, loop, and repair-cycle patterns.
- `graph/` for routing, branching, merge, and fan-out/fan-in.
- `llm/` for provider, retry, fallback, and schema-driven model work.
- `resilience/` for timeouts and rate limiting.
- `jsonschema/` for manual schema building and schema generation.
- `json/` for parser, mapper, and serializer usage.
- `batch/` for document and multi-item pipelines.
- `tools/` for tool execution and sandboxing.
- `advanced/` for repair loops and full end-to-end patterns.

Use these files as copy-paste starting points. They are intentionally small and focused on one concept at a time.

## Index

### Easy
- `easy/01_hello_world.java`
- `easy/02_models_profiles.java`
- `easy/03_prompt_templates.java`

### Modes
- `modes/01_execution_runtime_modes.java`
- `modes/02_collection_modes.java`
- `modes/03_schema_modes.java`
- `modes/04_rate_limit_modes.java`
- `modes/05_tool_sandbox_modes.java`

### LLM
- `llm/01_chain_and_fallback.java`
- `llm/02_schema_node.java`

### Graph
- `graph/01_branch_router_merge.java`
- `graph/02_map_gather.java`
- `graph/03_failure_modes.java`

### Loops
- `loops/01_repeat_same_node.java`
- `loops/02_loop_to_different_node.java`
- `loops/03_repair_cycle.java`

### Resilience
- `resilience/01_timeout_and_retry.java`

### JSON Schema
- `jsonschema/01_manual_schema_builder.java`
- `jsonschema/02_schema_from_class.java`
- `jsonschema/03_schema_node_with_manual_schema.java`

### JSON Utils
- `json/01_parse_json.java`
- `json/02_json_mapper.java`
- `json/03_json_serializer.java`

### Tools
- `tools/01_file_and_http_tools.java`

### Batch
- `batch/01_batch_document_pipeline.java`

### Advanced
- `advanced/01_repair_loop.java`
- `advanced/02_full_document_workflow.java`
