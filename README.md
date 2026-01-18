# OxyJen🫧

**OxyJen** is the missing AI framework for Java & JVM enterprises.  

**AI Orchestration Framework for Java** - Build complex AI pipelines with simplicity and power.

(CONTRIBUTING.md)

---

## What is Oxyjen?

Oxyjen is a **graph-based orchestration framework** for building AI applications in Java. It provides a clean, extensible architecture for connecting LLMs, data processors, and custom logic into powerful workflows.

Think of it as **the plumbing for your AI pipelines**, you focus on what each step does, Oxyjen handles the execution flow.

## "Why Oxyjen When LangChain4j Exists?"

**I get it, this is the first question you're thinking.** Let me be completely honest.

### The Story

I started building Oxyjen without knowing LangChain4j existed. When I discovered it halfway through, I had a choice:
1. Abandon the project
2. Find a way to differentiate

**I chose to differentiate.**
**I wanted to learn how OSS works.**
**I wanted to build this in public.**

### How Oxyjen Will Be Different

LangChain4j is a solid framework focused on **feature breadth**, lots of integrations, lots of tools. That's great for many use cases.

Oxyjen is taking a different path, focused on **developer experience and production readiness**

I'm planing on a lot of performance features like, async, project loom, parallel processing, java concurrency for v0.3, features like easiest llm calls and alot of AI agents for v0.2 and manymore.

I'm not here to compete with Langchain4j, I'm here to create a framework for devs.


### Why Oxyjen?

Modern AI applications need more than just API calls. They need:
- **Complex workflows** with multiple steps
- **Type safety** to catch errors at compile time
- **Observability** to debug what's happening
- **Testability** to ensure reliability
- **Extensibility** to add custom logic

Oxyjen provides all of this with a simple, intuitive API.

---

## Quick Example
```java
// Build a 3-step text processing pipeline
Graph pipeline = GraphBuilder.named("text-processor")
    .addNode(new UppercaseNode())
    .addNode(new ReverseNode())
    .addNode(new PrefixNode("OUTPUT: "))
    .build();

// Execute with context
NodeContext context = new NodeContext();
Executor executor = new Executor();

String result = executor.run(pipeline, "hello world", context);
System.out.println(result);
// Output: OUTPUT: DLROW OLLEH
```

That's it! Clean, simple, powerful.

---

## Architecture

Oxyjen is built around four core concepts:

### 1️**Graph** - The Pipeline Blueprint
A `Graph` defines the structure of your pipeline - which nodes run in what order.
```java
public class Graph {
    private final String name;
    private final List<NodePlugin<?, ?>> nodes;
    
    // Add nodes to your pipeline
    public Graph addNode(NodePlugin<?, ?> node);
    
    // Get all nodes in execution order
    public List<NodePlugin<?, ?>> getNodes();
}
```

**Think of it as:** Your pipeline's DNA - it knows what needs to happen, but doesn't execute anything.

### 2️**NodePlugin** - The Processing Unit
A `NodePlugin` is a single step in your pipeline. Each node transforms input into output.
```java
public interface NodePlugin<I, O> {
    // Core processing logic
    O process(I input, NodeContext context);
    
    // Unique identifier for this node
    default String getName() { 
        return this.getClass().getSimpleName(); 
    }
    
    // Lifecycle hooks for setup/cleanup
    default void onStart(NodeContext context) {}
    default void onFinish(NodeContext context) {}
    default void onError(Exception e, NodeContext context) {}
}
```

**Think of it as:** A Lego brick - small, focused, composable.

**Example node:**
```java
public class SummarizerNode implements NodePlugin<String, String> {
    @Override
    public String process(String input, NodeContext context) {
        context.getLogger().info("Summarizing text...");
        // Your logic here (will be LLM call in v0.2)
        return "Summary: " + input.substring(0, 100);
    }
    
    @Override
    public void onStart(NodeContext context) {
        context.getLogger().info("Summarizer node starting");
    }
}
```

### 3️**Executor** - The Runtime Engine
The `Executor` runs your graph, calling each node in sequence and passing outputs to inputs.
```java
public class Executor {
    public <I, O> O run(Graph graph, I input, NodeContext context) {
        // Validates graph structure
        // Executes nodes sequentially
        // Handles errors and lifecycle hooks
        // Returns final output
    }
}
```

**Think of it as:** The conductor of an orchestra - coordinates everything.

**How it works:**
1. Takes your `Graph` and initial `input`
2. For each node:
   - Calls `onStart()` lifecycle hook
   - Executes `process()` with current data
   - Calls `onFinish()` lifecycle hook
   - Passes output to next node
3. Returns final result

### 4️**NodeContext** - Shared Memory & State
The `NodeContext` is shared across all nodes, providing logging and state management.
```java
public class NodeContext {
    // Store/retrieve shared data
    public void set(String key, Object value);
    public <T> T get(String key);
    
    // Logging
    public Logger getLogger();
    public OxyLogger getOxyjenLogger();
    
    // Metadata (e.g., graph name, execution ID)
    public void setMetadata(String key, Object value);
    public <T> T getMetadata(String key);
    
    // Error handling
    public ExceptionHandler getExceptionHandler();
}
```

**Think of it as:** A shared notebook that all nodes can read/write to.

**Example usage:**
```java
public String process(String input, NodeContext ctx) {
    // Log what's happening
    ctx.getLogger().info("Processing: " + input);
    
    // Store intermediate results
    ctx.set("word_count", input.split(" ").length);
    
    // Share data between nodes
    String previousResult = ctx.get("previous_output");
    
    return processedOutput;
}
```

---

## Complete Working Example
```java
package examples;

import io.oxyjen.core.*;

public class ContentPipeline {
    
    public static void main(String[] args) {
        // Step 1: Define your nodes
        NodePlugin<String, String> validator = new ValidationNode();
        NodePlugin<String, String> processor = new ProcessingNode();
        NodePlugin<String, String> formatter = new FormatterNode();
        
        // Step 2: Build your graph
        Graph pipeline = GraphBuilder.named("content-pipeline")
            .addNode(validator)
            .addNode(processor)
            .addNode(formatter)
            .build();
        
        // Step 3: Create execution context
        NodeContext context = new NodeContext();
        context.set("max_length", 100);
        
        // Step 4: Execute
        Executor executor = new Executor();
        String result = executor.run(pipeline, "Raw input text", context);
        
        System.out.println("Final output: " + result);
        System.out.println("Word count: " + context.get("word_count"));
    }
}

// Example node implementations
class ValidationNode implements NodePlugin<String, String> {
    @Override
    public String process(String input, NodeContext ctx) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be empty");
        }
        ctx.getLogger().info("✓ Input validated");
        return input;
    }
}

class ProcessingNode implements NodePlugin<String, String> {
    @Override
    public String process(String input, NodeContext ctx) {
        String processed = input.toUpperCase().trim();
        ctx.set("word_count", processed.split(" ").length);
        ctx.getLogger().info("✓ Text processed");
        return processed;
    }
}

class FormatterNode implements NodePlugin<String, String> {
    @Override
    public String process(String input, NodeContext ctx) {
        Integer maxLength = ctx.get("max_length");
        String formatted = input.length() > maxLength 
            ? input.substring(0, maxLength) + "..." 
            : input;
        ctx.getLogger().info("✓ Text formatted");
        return formatted;
    }
}
```

---

## My Vision for Oxyjen

## Vision
- Bring AI orchestration (LangChain/LangGraph style) to Java.  
- Build enterprise-first modules: Spring Config Analyzer, GC/JVM Log Analyzer, Secure Workflow Engine.  
- Focus on **performance, security, and observability**, because enterprises need more than toys.
- I'm building this to learn java in a much deeper way.

### **Phase 1: Foundation (v0.1 - DONE)**
Clean, extensible architecture that makes building pipelines intuitive.

### **Phase 2: AI Integration (v0.2 - Next 1-2 Weeks)**
First-class support for LLM providers:
- **OpenAI** (GPT-4, GPT-3.5)
- **Anthropic** (Claude Sonnet, Opus)
- **Google** (Gemini Pro)
- Streaming responses
- Token counting & cost tracking
- Prompt templates

**Example (coming soon):**
```java
Graph aiPipeline = GraphBuilder.named("content-generator")
    .addNode(new ClaudeChatNode("claude-sonnet-4")
        .withSystemPrompt("You are a helpful assistant")
        .withTemperature(0.7))
    .addNode(new FormatterNode())
    .build();

String blog = executor.run(aiPipeline, "Write about AI", context);
```

### **Phase 3: Advanced Orchestration (v0.3 - 4-6 Weeks)**
- **Async execution** - Run nodes in parallel
- **DAG support** - Complex branching workflows
- **Conditional routing** - "If X, then run node Y"
- **Retry logic** - Automatic retries with backoff
- **Circuit breakers** - Fail fast when services are down


### **Also Planning on: Production & Enterprise (v0.4+)**
- **RAG support** - Vector databases, embeddings, document loaders
- **Cost management** - Budgets, limits, usage tracking
- **Enterprise features** - Audit logs, RBAC, compliance
- **Multi-tenancy** - Isolate data between users/orgs

---

## Installation

[![](https://jitpack.io/v/11divyansh/Oxyjen.svg)](https://jitpack.io/#11divyansh/Oxyjen/v0.1.0)


### Maven

**Add JitPack repository:**
```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

**Add dependency:**
```xml
<dependency>
  <groupId>com.github.11divyansh</groupId>
  <artifactId>Oxyjen</artifactId>
  <version>v0.1.0</version>
</dependency>
```

### Gradle
```gradle
repositories {
  maven { url 'https://jitpack.io' }
}

dependencies {
  implementation 'com.github.11divyansh:Oxyjen:v0.1.0'
}
```

### Build from Source
```bash
git clone https://github.com/11divyansh/OxyJen.git
cd OxyJen
mvn clean install
```

After installation, verify by importing:

```java
import io.oxyjen.core.*;
```
---

## About

Built with ❤️ by [Divyansh](https://github.com/11divyansh) - a BTech CS student who believes Java deserves world-class AI tooling.

**This started as a learning project, but I'm committed to making it production-ready. I know this is not big yet, but lets make it valuable.**

### Get Involved

- **Star this repo** to follow the journey and be a part of it
- **Report bugs** via [Issues](../../issues)
- **Suggest features** via [Discussions](../../discussions)
- **Contribute** code or documentation
- **Share** on Twitter/LinkedIn if you find it useful

---

** Watch for updates on v0.2 progress!**

## License
[Apache 2.0](LICENSE) (open-source, enterprise-friendly) 
