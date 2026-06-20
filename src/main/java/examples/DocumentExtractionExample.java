package examples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.graph.ParallelExecutor;
import io.oxyjen.graph.branching.BranchNode;
import io.oxyjen.graph.branching.MergeNode;
import io.oxyjen.graph.branching.RouterNode;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.ParallelNode;
import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.llm.LLMNode;
import io.oxyjen.llm.prompts.PromptTemplate;
import io.oxyjen.llm.prompts.Variable;
import io.oxyjen.llm.schema.SchemaGenerator;
import io.oxyjen.llm.schema.SchemaNode;
import io.oxyjen.llm.transport.gemini.GeminiChatModel;

/**
 * Runnable single-document extraction workflow for Oxyjen v0.5.
 *
 * Problem statement:
 * - Take one business document.
 * - Classify it.
 * - Route it to the right extraction path.
 * - Run deterministic local checks plus LLM-backed analysis.
 * - Merge everything into one final report.
 *
 * Features shown here:
 * - `PromptTemplate` + `Variable` for clean prompt assembly
 * - `SchemaNode` for typed structured extraction
 * - `BranchNode` for one-of-many routing
 * - `RouterNode` for fan-out to multiple downstream analyses
 * - `ParallelNode` for pure Java checks
 * - `GatherNode` for collecting those checks
 * - `MergeNode` for fan-in and final report assembly
 * - `connectAnyFailureTo(...)` for graph-wide failure handling
 *
 * Set `GEMINI_API_KEY` and run this class. Optionally pass a text file path as the first argument.
 */
public final class DocumentExtractionExample {

	private static final String DEFAULT_MODEL  = "gemini/gemini-flash-latest";
	private static final String FALLBACK_MODEL = "gemini/gemini-flash-latest";

    private DocumentExtractionExample() {}

    public static void main(String[] args) throws Exception {
        // Gemini is wrapped in LLMChain so retries, timeout, jitter, and fallback
        // behavior are explicit instead of hidden in the graph.
        String modelName = envOrDefault("OXYJEN_GEMINI_MODEL", DEFAULT_MODEL);
        String fallbackModelName = envOrDefault("OXYJEN_GEMINI_FALLBACK_MODEL", FALLBACK_MODEL);
        String document = args.length > 0
                ? Files.readString(Path.of(args[0]))
                : sampleInvoice();

        ChatModel primary = geminiModel(modelName);
        ChatModel fallback = geminiModel(fallbackModelName);
        LLMChain chain = LLMChain.builder()
                .primary(primary)
                .fallback(fallback)
                .retry(3)
                .timeout(Duration.ofSeconds(45))
                .exponentialBackoff()
                .maxBackoff(Duration.ofSeconds(60))
                .jitter(0.15)
                .build();

        Graph graph = buildGraph(chain);

        ExecutionRuntime runtime = ExecutionRuntime.builder()
                .maxConcurrency(3)
                .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
                .defaultTimeout(60, TimeUnit.SECONDS)
                .build();

        NodeContext context = new NodeContext();
        Map<String, Object> output = new ParallelExecutor(runtime)
                .run(graph, document, context);

        System.out.println("\n=== Document Extraction Report ===\n");
        System.out.println(output.get("finalReport"));
        if (output.containsKey("failureReport")) {
            System.out.println("\n=== Failure Report ===\n");
            System.out.println(output.get("failureReport"));
        }
    }

    private static Graph buildGraph(ChatModel chain) {
        // Phase 1: extract and validate a classification record so routing stays deterministic.
        SchemaNode<DocumentClassification> classify = SchemaNode.builder(DocumentClassification.class)
                .model(chain)
                .schema(SchemaGenerator.fromClass(DocumentClassification.class))
                .maxRetries(5)
                .memory("document-extraction")
                .build();

        // Reuse the same extractor shape for each document family. Only the prompt changes.
        SchemaNode<DocumentExtraction> invoiceExtractor = extractionNode(chain);
        SchemaNode<DocumentExtraction> contractExtractor = extractionNode(chain);
        SchemaNode<DocumentExtraction> genericExtractor = extractionNode(chain);

        // Typed downstream analyses for risk and action planning.
        SchemaNode<RiskAssessment> riskExtractor = SchemaNode.builder(RiskAssessment.class)
                .model(chain)
                .schema(SchemaGenerator.fromClass(RiskAssessment.class))
                .maxRetries(2)
                .memory("document-extraction")
                .build();

        SchemaNode<ActionPlan> actionExtractor = SchemaNode.builder(ActionPlan.class)
                .model(chain)
                .schema(SchemaGenerator.fromClass(ActionPlan.class))
                .maxRetries(2)
                .memory("document-extraction")
                .build();

        LLMNode summaryLlm = LLMNode.builder()
                .model(chain)
                .memory("document-extraction")
                .build();

        LLMNode finalReportLlm = LLMNode.builder()
                .model(chain)
                .memory("document-extraction")
                .build();

        LLMNode failureLlm = LLMNode.builder()
                .model(chain)
                .memory("document-extraction")
                .build();

        // Local deterministic checks that do not need model calls.
        ParallelNode<DocumentExtraction, String> localChecks = ParallelNode
                .<DocumentExtraction, String>builder()
                .task("amount-check", extraction -> hasValue(extraction.totalAmount())
                        ? "total amount present: " + extraction.totalAmount()
                        : "total amount missing")
                .task("due-date-check", extraction -> hasValue(extraction.dueDate())
                        ? "due date present: " + extraction.dueDate()
                        : "due date missing")
                .task("party-check", extraction -> hasValue(extraction.issuer()) && hasValue(extraction.recipient())
                        ? "issuer and recipient present"
                        : "issuer or recipient missing")
                .continueOnError()
                .timeout(30, TimeUnit.SECONDS)
                .build("localChecks");

        // GatherNode reshapes the ParallelNode output into an ordered list for the merge step.
        GatherNode gatherChecks = GatherNode.builder()
                .collectMode(CollectionMode.SUCCESS_ONLY)
                .sortBy(Comparator.comparing(Object::toString))
                .aggregate(GatherNode.Aggregation.LIST)
                .build("gatherChecks");

        // MergeNode waits for the analysis branches and the local checks before the final report.
        MergeNode merge = new MergeNode.Builder()
                .expect("summary", "risk", "actions", "gatherChecks")
                .timeout(45, TimeUnit.SECONDS)
                .build("merge");

        // Flow:
        // start -> classifyPrompt -> classify -> branch
        // branch -> extract prompt -> schema extraction
        // extraction -> router -> summary / risk / actions / local checks
        // merge -> final report
        return GraphBuilder.named("document-extraction")
                .addNode("start", new CaptureDocumentNode())
                .addNode("classifyPrompt", new ClassificationPromptNode())
                .addNode("classify", classify)
                .addNode("branch", BranchNode.<DocumentClassification>builder()
                        .when("invoice", c -> "invoice".equalsIgnoreCase(c.type()))
                            .then("invoicePrompt")
                        .when("contract", c -> "contract".equalsIgnoreCase(c.type()))
                            .then("contractPrompt")
                        .orElse("genericPrompt")
                        .build("branch"))
                .addNode("invoicePrompt", new ExtractionPromptNode("invoice"))
                .addNode("contractPrompt", new ExtractionPromptNode("contract"))
                .addNode("genericPrompt", new ExtractionPromptNode("generic business document"))
                .addNode("extractInvoice", invoiceExtractor)
                .addNode("extractContract", contractExtractor)
                .addNode("extractGeneric", genericExtractor)
                .addNode("analysisFanout", RouterNode.<DocumentExtraction>builder()
                        .route("summary", extraction -> true, "summaryPrompt")
                        .route("risk", extraction -> true, "riskPrompt")
                        .route("actions", extraction -> true, "actionsPrompt")
                        .build("analysisFanout"))
                .addNode("summaryPrompt", new SummaryPromptNode())
                .addNode("riskPrompt", new RiskPromptNode())
                .addNode("actionsPrompt", new ActionsPromptNode())
                .addNode("summary", summaryLlm)
                .addNode("risk", riskExtractor)
                .addNode("actions", actionExtractor)
                .addNode("localChecks", localChecks)
                .addNode("gatherChecks", gatherChecks)
                .addNode("merge", merge)
                .addNode("finalReportPrompt", new FinalReportPromptNode())
                .addNode("finalReport", finalReportLlm)
                .addNode("failurePrompt", new FailurePromptNode())
                .addNode("failureReport", failureLlm)
                .connect("start", "classifyPrompt")
                .connect("classifyPrompt", "classify")
                .connect("classify", "branch")
                .connect("branch", "invoicePrompt")
                .connect("branch", "contractPrompt")
                .connect("branch", "genericPrompt")
                .connect("invoicePrompt", "extractInvoice")
                .connect("contractPrompt", "extractContract")
                .connect("genericPrompt", "extractGeneric")
                .connect("extractInvoice", "analysisFanout")
                .connect("extractContract", "analysisFanout")
                .connect("extractGeneric", "analysisFanout")
                .connect("analysisFanout", "summaryPrompt")
                .connect("analysisFanout", "riskPrompt")
                .connect("analysisFanout", "actionsPrompt")
                .connect("summaryPrompt", "summary")
                .connect("riskPrompt", "risk")
                .connect("actionsPrompt", "actions")
                .connect("localChecks", "gatherChecks")
                .connect("analysisFanout", "localChecks")
                .connect("summary", "merge")
                .connect("risk", "merge")
                .connect("actions", "merge")
                .connect("gatherChecks", "merge")
                .connect("merge", "finalReportPrompt")
                .connect("finalReportPrompt", "finalReport")
                .connect("failurePrompt", "failureReport")
                .connectAnyFailureTo("failurePrompt")
                .build();
    }

    private static SchemaNode<DocumentExtraction> extractionNode(ChatModel chain) {
        // Reusable typed extraction node for invoice / contract / generic document shapes.
        return SchemaNode.builder(DocumentExtraction.class)
                .model(chain)
                .schema(SchemaGenerator.fromClass(DocumentExtraction.class))
                .maxRetries(5)
                .memory("document-extraction")
                .build();
    }

    private static ChatModel geminiModel(String modelName) {
    	ChatModel model = LLM.of(modelName);
        if (model instanceof GeminiChatModel gemini) {
            return gemini.withTemperature(0.0).withMaxTokens(4096);
        }
        return model;
    }

//    private static String requireEnv(String name) {
//        String value = System.getenv(name);
//        if (value == null || value.isBlank()) {
//            throw new IllegalStateException(
//                    name + " is required. Set it before running this example."
//            );
//        }
//        return value;
//    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value);
    }

    public record DocumentClassification(
            String type,
            String language,
            String reason
    ) {}

    public record DocumentExtraction(
            String documentType,
            String title,
            String issuer,
            String recipient,
            String date,
            String dueDate,
            String totalAmount,
            String currency,
            List<String> keyFields,
            List<String> obligations,
            List<String> missingFields
    ) {}

    public record RiskAssessment(
            String riskLevel,
            List<String> risks,
            List<String> reasons
    ) {}

    public record ActionPlan(
            List<String> nextActions,
            List<String> humanReviewReasons
    ) {}

    static final class CaptureDocumentNode implements NodePlugin<String, String> {
        @Override
        public String process(String input, NodeContext context) {
            // Store the raw document once so all later prompt nodes can reuse it.
            context.set("documentText", input);
            return input;
        }
    }

    static final class ClassificationPromptNode implements NodePlugin<String, String> {
        private static final PromptTemplate TEMPLATE = PromptTemplate.of(
        		"""
                Classify this business document.

                You MUST return all three fields:
                - type: one of: invoice, contract, receipt, purchase_order, report, other
                - language: the language the document is written in (e.g. "English", "French")
                - reason: one sentence explaining why you chose that type

                Example output:
                {"type": "invoice", "language": "English", "reason": "Document contains vendor, billing party, line items and a total due amount."}

                Document:
                {{document}}
                """,
                Variable.required("document")
        );

        @Override
        public String process(String input, NodeContext context) {
            // PromptTemplate + Variable make the prompt explicit and easy to maintain.
            return TEMPLATE.render("document", input);
        }
    }

    static final class ExtractionPromptNode implements NodePlugin<DocumentClassification, String> {
        private static final PromptTemplate TEMPLATE = PromptTemplate.of(
                """
                Extract structured fields from this {{document_kind}}.

                Classification:
                type={{type}}
                language={{language}}
                reason={{reason}}

                Rules:
                - Use "unknown" for absent scalar fields.
                - Use [] for absent lists.
                - Keep totalAmount as text exactly as found.
                - obligations should include payment terms, contract duties, deadlines, or follow-ups.

                Document:
                {{document}}
                """,
                Variable.required("document_kind"),
                Variable.required("type"),
                Variable.required("language"),
                Variable.required("reason"),
                Variable.required("document")
        );

        private final String kind;

        ExtractionPromptNode(String kind) {
            this.kind = kind;
        }

        @Override
        public String process(DocumentClassification classification, NodeContext context) {
            // This prompt is assembled after branching, so each document type gets a different extractor.
            return TEMPLATE.render(
                    "document_kind", kind,
                    "type", classification.type(),
                    "language", classification.language(),
                    "reason", classification.reason(),
                    "document", context.get("documentText")
            );
        }
    }

    static final class SummaryPromptNode implements NodePlugin<DocumentExtraction, String> {
        private static final PromptTemplate TEMPLATE = PromptTemplate.of(
                """
                Write a concise operational summary of this extracted document.
                Mention the document type, parties, amount, due date, and the most important obligations.

                Extraction:
                {{extraction}}
                """,
                Variable.required("extraction")
        );

        @Override
        public String process(DocumentExtraction extraction, NodeContext context) {
            // RouterNode fan-out starts here: each downstream prompt gets the same typed extraction.
            context.set("extraction", extraction);
            return TEMPLATE.render("extraction", extraction);
        }
    }

    static final class RiskPromptNode implements NodePlugin<DocumentExtraction, String> {
        private static final PromptTemplate TEMPLATE = PromptTemplate.of(
                """
                Assess this extracted document for operational risk.
                riskLevel must be LOW, MEDIUM, or HIGH.
                Flag missing due dates, missing totals, unusual obligations, unclear counterparties, or anything requiring human review.

                Extraction:
                {{extraction}}
                """,
                Variable.required("extraction")
        );

        @Override
        public String process(DocumentExtraction extraction, NodeContext context) {
            // Risk analysis is isolated so it can evolve independently from the summary prompt.
            return TEMPLATE.render("extraction", extraction);
        }
    }

    static final class ActionsPromptNode implements NodePlugin<DocumentExtraction, String> {
        private static final PromptTemplate TEMPLATE = PromptTemplate.of(
                """
                Produce a practical action plan for an operations team.
                Include payment, filing, follow-up, and human-review actions where relevant.

                Extraction:
                {{extraction}}
                """,
                Variable.required("extraction")
        );

        @Override
        public String process(DocumentExtraction extraction, NodeContext context) {
            // Action planning is kept separate from risk and summary for cleaner prompt boundaries.
            return TEMPLATE.render("extraction", extraction);
        }
    }

    static final class FinalReportPromptNode implements NodePlugin<Map<String, Object>, String> {
        private static final PromptTemplate TEMPLATE = PromptTemplate.of(
                """
                Create a final document extraction report for an operations user.

                Requirements:
                - Use clear section headings.
                - Include extracted fields.
                - Include summary, risks, local deterministic checks, and recommended actions.
                - Mention any extraction errors.
                - Do not invent facts not present in the data.

                Merge result:
                successes={{successes}}
                errors={{errors}}
                """,
                Variable.required("successes"),
                Variable.required("errors")
        );

        @Override
        public String process(Map<String, Object> input, NodeContext context) {
            return TEMPLATE.render(
                    "successes", input.get("successes"),
                    "errors", input.get("errors")
            );
        }
    }

    static final class FailurePromptNode implements NodePlugin<Object, String> {
        private static final PromptTemplate TEMPLATE = PromptTemplate.of(
                """
                A document extraction graph node failed.
                Explain what failed, what partial data may still be usable, and what a human should do next.

                Failure object:
                {{failure}}
                """,
                Variable.required("failure")
        );

        @Override
        public String process(Object input, NodeContext context) {
            // Any graph failure is routed here by connectAnyFailureTo("failurePrompt").
            return TEMPLATE.render("failure", input);
        }
    }

    private static String sampleInvoice() {
        return """
                INVOICE
                Vendor: Northwind Analytics Pvt Ltd
                Bill To: Acme Retail Operations
                Invoice No: INV-2026-0417
                Date: 2026-05-22
                Due Date: 2026-06-06

                Services:
                - Demand forecasting dashboard setup: USD 1,200
                - Data cleanup and SKU mapping: USD 450
                - Monthly analytics support: USD 350

                Total Due: USD 2,000

                Payment Terms:
                Payment is due within 15 days by bank transfer. Please include invoice number in the payment reference.
                Contact finance@northwind.example for billing questions.
                """;
    }
}
