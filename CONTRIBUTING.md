# Contributing to Oxyjen

First off, **thank you** for considering contributing to Oxyjen!

This project is built in public and community-driven. Whether you're fixing a typo, adding a feature, or just providing feedback - every contribution matters.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Pull Request Process](#pull-request-process)
- [Coding Guidelines](#coding-guidelines)
- [Project Structure](#project-structure)
- [Current Priorities](#current-priorities)
- [Recognition](#recognition)

---

## Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inspiring community for everyone. Please be respectful, constructive, and kind.

### Unacceptable Behavior

- Harassment, trolling, or insulting comments
- Personal or political attacks
- Publishing others' private information
- Any conduct that would be inappropriate in a professional setting

### Enforcement

If you experience or witness unacceptable behavior, please report it by opening an issue or contacting [@11divyansh](https://github.com/11divyansh).

---

## How Can I Contribute?

### 1. Reporting Bugs

Found a bug? Help us fix it!

**Before submitting:**
- Check if the bug has already been reported in [Issues](../../issues)
- Make sure you're using the latest version

**How to report:**
1. Go to [Issues](../../issues/new)
2. Use the "Bug Report" template if available
3. Provide:
   - Clear title (e.g., "Executor throws NPE when graph is empty")
   - Description of what happened vs what you expected
   - Steps to reproduce
   - Java version, OS, and Oxyjen version
   - Code snippet if possible
   - Stack trace or error message

**Example:**
````markdown
**Title:** Executor crashes when NodeContext is null

**Description:** 
The Executor throws NullPointerException when NodeContext is null, 
but it should throw a more descriptive error.

**Steps to Reproduce:**
1. Create a Graph with one node
2. Call executor.run(graph, input, null)
3. NPE is thrown

**Expected:** IllegalArgumentException with message "NodeContext cannot be null"
**Actual:** NullPointerException

**Environment:**
- Oxyjen v0.1.0
- Java 11
````

---

### 2. Suggesting Features

Have an idea? We'd love to hear it!

**Before suggesting:**
- Check [Issues](../../issues) and [Discussions](../../discussions) for similar ideas
- Consider if it aligns with [Oxyjen's vision](README.md#-my-vision-for-oxyjen)

**How to suggest:**
1. Go to [Issues](../../issues/new) or [Discussions](../../discussions/new)
2. Use "Feature Request" label
3. Provide:
   - Clear title describing the feature
   - Use case - what problem does it solve?
   - Proposed solution
   - Alternative solutions you considered
   - Code examples (if applicable)

**Example:**
````markdown
**Title:** Add support for conditional node execution

**Use Case:**
I want to run different nodes based on previous outputs. 
E.g., if sentiment is negative, run escalation node, 
else run standard response node.

**Proposed Solution:**
```java
Graph pipeline = GraphBuilder.named("conditional")
    .addNode(new SentimentNode())
    .addConditionalBranch(
        condition: ctx -> ctx.get("sentiment").equals("negative"),
        ifTrue: new EscalationNode(),
        ifFalse: new StandardNode()
    )
    .build();
```

**Alternatives:**
- Use separate graphs for each branch
- Implement logic inside node (but this mixes concerns)
````

---

### 3. Contributing Code

This is where the magic happens! Here's how to contribute code:

---

## Getting Started

### Prerequisites

- **Java 11+** (Java 17+ recommended)
- **Maven 3.6+**
- **Git**
- Your favorite IDE (IntelliJ IDEA, Eclipse, VS Code)

### Fork & Clone

1. **Fork the repository**
   - Click "Fork" button on [GitHub](https://github.com/11divyansh/OxyJen)

2. **Clone your fork**
````bash
   git clone https://github.com/YOUR_USERNAME/OxyJen.git
   cd OxyJen
````

3. **Add upstream remote**
````bash
   git remote add upstream https://github.com/11divyansh/OxyJen.git
````

4. **Verify remotes**
````bash
   git remote -v
   # origin    https://github.com/YOUR_USERNAME/OxyJen.git (fetch)
   # origin    https://github.com/YOUR_USERNAME/OxyJen.git (push)
   # upstream  https://github.com/11divyansh/OxyJen.git (fetch)
   # upstream  https://github.com/11divyansh/OxyJen.git (push)
````

### Build & Test
````bash
# Build the project
mvn clean install

# Run tests
mvn test

# Run specific test
mvn test -Dtest=ExecutorTest

# Skip tests (not recommended!)
mvn clean install -DskipTests
````

**Expected output:**
````
[INFO] BUILD SUCCESS
[INFO] Total time: 5.234 s
````

---

## Development Workflow

### Step 1: Create a Branch

**Always create a new branch for your work. Never commit directly to `main`.**
````bash
# Update your local main
git checkout main
git pull upstream main

# Create feature branch
git checkout -b feature/your-feature-name

# Or for bug fixes
git checkout -b fix/bug-description
````

**Branch naming conventions:**
- `feature/add-openai-integration` - New features
- `fix/executor-null-pointer` - Bug fixes
- `docs/improve-readme` - Documentation
- `refactor/simplify-context` - Code refactoring
- `test/add-executor-tests` - Adding tests

### Step 2: Make Your Changes

**Write clean, focused code:**
- One feature/fix per branch
- Keep changes small and reviewable
- Write tests for new functionality
- Update documentation if needed

**Example workflow:**
````bash
# Make changes to files
vim src/main/java/io/oxyjen/core/Executor.java

# Run tests frequently
mvn test

# Add files
git add src/main/java/io/oxyjen/core/Executor.java

# Commit with clear message
git commit -m "feat: add retry logic to Executor"
````

### Step 3: Keep Your Branch Updated
````bash
# Fetch latest changes from upstream
git fetch upstream

# Rebase your branch on latest main
git rebase upstream/main

# If conflicts occur, resolve them, then:
git add .
git rebase --continue
````

### Step 4: Push Your Changes
````bash
# Push to your fork
git push origin feature/your-feature-name

# If you rebased, you might need force push
git push origin feature/your-feature-name --force-with-lease
````

---

## Pull Request Process

### Before Opening a PR

**Checklist:**
- [ ] Code builds successfully (`mvn clean install`)
- [ ] All tests pass (`mvn test`)
- [ ] Added tests for new features
- [ ] Updated documentation (JavaDocs, README)
- [ ] Followed [coding guidelines](#coding-guidelines)
- [ ] Commits are clean and descriptive
- [ ] Branch is up-to-date with `main`

### Opening a PR

1. Go to [Pull Requests](../../pulls)
2. Click "New Pull Request"
3. Select:
   - **base:** `11divyansh/OxyJen` → `main`
   - **compare:** `YOUR_USERNAME/OxyJen` → `feature/your-feature`
4. Fill in the template:
````markdown
## Description
Brief description of what this PR does.

## Related Issues
Fixes #123
Relates to #456

## Changes Made
- Added retry logic to Executor
- Updated Executor tests
- Added JavaDoc for new methods

## Testing
- [ ] Unit tests added/updated
- [ ] Manual testing completed
- [ ] All tests pass locally

## Screenshots (if applicable)
[Add screenshots for UI changes]

## Checklist
- [ ] Code builds successfully
- [ ] Tests pass
- [ ] Documentation updated
- [ ] Follows coding guidelines
````

### PR Review Process

1. **Automated checks run** (if CI is set up)
2. **Maintainer reviews** within 24-48 hours
3. **You address feedback** by pushing new commits
4. **Approval & merge** once everything looks good

**During review:**
- Be open to feedback
- Ask questions if something is unclear
- Make requested changes promptly
- Be patient and professional

**After merge:**
- Your branch will be deleted (automatically or manually)
- You'll be credited in release notes
- Your name will be added to Contributors list

---

## Coding Guidelines

### Java Style

**Follow standard Java conventions:**
````java
// Good
public class OpenAIClient {
    private final String apiKey;
    
    public OpenAIClient(String apiKey) {
        this.apiKey = Objects.requireNonNull(apiKey);
    }
    
    /**
     * Sends a chat completion request to OpenAI.
     * 
     * @param request The chat request
     * @return The response from OpenAI
     * @throws IOException if network error occurs
     */
    public ChatResponse chat(ChatRequest request) throws IOException {
        // Implementation
    }
}

// Bad
public class openAIClient {
    String apiKey; // Not final, not private
    
    public openAIClient(String key) {
        apiKey = key; // No null check
    }
    
    // No JavaDoc
    public ChatResponse chat(ChatRequest request) {
        // Implementation
    }
}
````

### Code Quality Rules

**1. Naming**
- Classes: `PascalCase` (e.g., `OpenAIClient`, `NodeContext`)
- Methods/variables: `camelCase` (e.g., `processInput`, `apiKey`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRIES`)
- Packages: `lowercase` (e.g., `io.oxyjen.core`)

**2. Methods**
- Keep methods short (<50 lines ideally)
- One responsibility per method
- Use descriptive names (avoid abbreviations)
````java
// Good
public String formatResponse(String raw) {
    String trimmed = trim(raw);
    String escaped = escapeHtml(trimmed);
    return addMetadata(escaped);
}

// Bad
public String fmt(String r) {
    return doStuff(r);
}
````

**3. Error Handling**
- Use specific exceptions
- Provide helpful error messages
- Don't swallow exceptions
````java
// Good
if (input == null) {
    throw new IllegalArgumentException(
        "Input cannot be null. Provide a valid input string."
    );
}

// Bad
if (input == null) {
    throw new Exception("error");
}
````

**4. JavaDoc**
- Document all public classes and methods
- Explain WHY, not just WHAT
- Include `@param`, `@return`, `@throws`
````java
/**
 * Executes a graph pipeline with the given input.
 * 
 * <p>This method runs each node sequentially, passing the output
 * of one node as the input to the next. If any node fails, execution
 * stops and an exception is thrown.
 * 
 * @param graph The pipeline to execute
 * @param input The initial input for the first node
 * @param context Shared context for all nodes
 * @param <I> Type of initial input
 * @param <O> Type of final output
 * @return The output from the last node in the pipeline
 * @throws RuntimeException if any node fails during execution
 */
public <I, O> O run(Graph graph, I input, NodeContext context) {
    // Implementation
}
````

**5. Tests**
- Write tests for new features
- Use descriptive test names
- Follow Arrange-Act-Assert pattern
````java
@Test
void executorShouldThrowExceptionWhenGraphIsEmpty() {
    // Arrange
    Graph emptyGraph = new Graph("empty");
    NodeContext context = new NodeContext();
    Executor executor = new Executor();
    
    // Act & Assert
    assertThrows(IllegalStateException.class, () -> {
        executor.run(emptyGraph, "input", context);
    });
}
````

---

## Project Structure
````
OxyJen/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── io/oxyjen/
│   │           ├── core/           # Core classes
│   │           │   ├── Executor.java
│   │           │   ├── Graph.java
│   │           │   ├── GraphBuilder.java
│   │           │   ├── NodePlugin.java
│   │           │   └── NodeContext.java
│   │           ├── llm/            # LLM integrations (v0.2+)
│   │           ├── tools/          # Tool integrations (v0.3+)
│   │           └── util/           # Utilities
│   └── test/
│       └── java/
│           └── io/oxyjen/
│               └── core/           # Tests mirror main structure
├── examples/                       # Example usage(not added yet)
├── docs/                           # Documentation
├── pom.xml
├── README.md
├── CONTRIBUTING.md
└── LICENSE
````

**Where to add your code:**
- New LLM provider → `src/main/java/io/oxyjen/llm/`
- New utility → `src/main/java/io/oxyjen/util/`
- New example → `examples/`(not added yet)
- Tests → Mirror the main structure in `src/test/`

---

## Current Priorities

### High Priority (v0.2 - Next 1-2 Weeks)

**Help wanted on these tasks:**

#### 1. OpenAI Integration
**Goal:** Add ChatGPT support with streaming

**Tasks:**
- [ ] Create `OpenAIClient` with HTTP client
- [ ] Implement chat completions endpoint
- [ ] Add streaming support (Server-Sent Events)
- [ ] Create `OpenAIChatNode` implementing `NodePlugin`
- [ ] Add token counting
- [ ] Write comprehensive tests
- [ ] Add example usage in `examples/`

**Resources:**
- [OpenAI API Docs](https://platform.openai.com/docs/api-reference/chat)
- Use `java.net.http.HttpClient` (Java 11+)

**Related:** [Issue #1](../../issues/1) *(create this issue)*

---

### Medium Priority (v0.3+)

- [ ] Async execution with `CompletableFuture`
- [ ] DAG (Directed Acyclic Graph) support
- [ ] Conditional branching
- [ ] Retry logic with exponential backoff
- [ ] Circuit breaker pattern
- [ ] Performance benchmarking

### Good First Issues

**New to the project? Start here:**

- [ ] Add examples
- [ ] Improve JavaDoc coverage
- [ ] Write unit tests for existing classes
- [ ] Fix typos in documentation
- [ ] Add inline code comments for complex logic
- [ ] Create utility methods (e.g., `StringUtils`, `JsonUtils`)

**Look for issues labeled:** `good-first-issue` or `help-wanted`

---

## Recognition

**All contributors are valued and recognized:**

### Contributors List
Your name will be added to:
- README Contributors section
- Release notes for the version you contributed to
- GitHub's Contributors page

### Special Recognition
- **First-time contributors** get a special shoutout
- **Significant contributions** get highlighted on social media
- **Regular contributors** may be invited as collaborators

### Hall of Fame (Coming Soon)
We'll track:
- Most commits
- Most issues resolved
- Most helpful reviewer
- Best documentation

---

## Questions?

**Need help or have questions?**

- **Discussions:** [Start a discussion](../../discussions)
- **Issues:** [Check existing issues](../../issues)
- **Email:** *(Add your email if you want)*
- **Twitter:** *(Add your Twitter if you want)*

**Don't be shy!** No question is too basic. We're all learning together.

---

## Additional Resources

- [Java Code Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html)
- [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/)
- [GitHub Flow](https://guides.github.com/introduction/flow/)

---

## Thank You!

Every contribution, no matter how small, makes Oxyjen better.

**You're helping build the future of AI orchestration in Java.**

---

*This document is a living guide. If you see ways to improve it, please submit a PR!*
