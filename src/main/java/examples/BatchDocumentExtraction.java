package examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import examples.DocumentExtractionExample.DocumentExtraction;
import examples.DocumentExtractionExample.RiskAssessment;
import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.execution.ExecutionRuntime.FailureMode;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.graph.ParallelExecutor;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.MapNode;
import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.llm.LLMNode;
import io.oxyjen.llm.prompts.PromptTemplate;
import io.oxyjen.llm.prompts.Variable;
import io.oxyjen.llm.schema.SchemaGenerator;
import io.oxyjen.llm.schema.SchemaNode;
import io.oxyjen.llm.transport.gemini.GeminiChatModel;

public class BatchDocumentExtraction {

	private static final String DEFAULT_MODEL  = "gemini/gemini-flash-latest";
	private static final String FALLBACK_MODEL = "gemini/gemini-flash-latest";

	/**
	 * Batch document processing example for Oxyjen v0.5.
	 *
	 * Problem statement:
	 * - Take a list of documents.
	 * - Extract structured fields from each document concurrently.
	 * - Run a second risk pass over the extracted records.
	 * - Aggregate the results.
	 * - Produce one batch-level report and one failure report path.
	 *
	 * Features shown here:
	 * - `MapNode` for batch fan-out over many inputs
	 * - `SchemaNode` for typed extraction and typed risk scoring
	 * - `LLMChain` for retries, fallback, timeout, and backoff
	 * - `GatherNode` for collecting and reshaping batch outputs
	 * - `PromptTemplate` + `Variable` for readable prompt construction
	 * - `connectAnyFailureTo(...)` for a shared failure handler
	 */
	public static void main(String[] args) throws IOException {
		// Build the Gemini-backed chain once and reuse it across the whole batch graph.
		String apiKey = requireEnv("GEMINI_API_KEY");
	    String modelName = envOrDefault("OXYJEN_GEMINI_MODEL", DEFAULT_MODEL);
	    String fallbackModelName = envOrDefault("OXYJEN_GEMINI_FALLBACK_MODEL", FALLBACK_MODEL);

	    // The batch example consumes multiple documents instead of one document string.
	    List<String> documents = args.length > 0
	            ? List.of(Files.readString(Path.of(args[0])))
	            : sampleDocuments(); // returns List<String>

	    ChatModel primary = geminiModel(apiKey, modelName);
	    ChatModel fallback = geminiModel(apiKey, fallbackModelName);
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
	            .failureMode(FailureMode.COLLECT_ERRORS)
	            .defaultTimeout(300, TimeUnit.SECONDS)
	            .build();

	    NodeContext context = new NodeContext();
	    Map<String, Object> output = new ParallelExecutor(runtime)
	            .run(graph, documents, context);  // pass List<String> not single document

	    System.out.println("\n=== Batch Document Extraction Report ===\n");
	    System.out.println(output.get("batchReport"));
	    if (output.get("failureReport") != null) {
	        System.out.println("\n=== Failure Report ===\n");
	        System.out.println(output.get("failureReport"));
	    }

	}
	private static Graph buildGraph(ChatModel chain) {

		// Typed extractor reused for every document in the batch.
		SchemaNode<DocumentExtraction> extractor = SchemaNode.builder(DocumentExtraction.class)
		        .model(chain)
		        .schema(SchemaGenerator.fromClass(DocumentExtraction.class))
		        .maxRetries(3)
		        .build();

		// Risk scoring is also typed so batch outputs remain structured.
		SchemaNode<RiskAssessment> riskNode = SchemaNode.builder(RiskAssessment.class)
		        .model(chain)
		        .schema(SchemaGenerator.fromClass(RiskAssessment.class))
		        .maxRetries(2)
		        .build();
		
	    // MapNode: same extraction function is applied to every document concurrently.
	    MapNode<String, DocumentExtraction> batchExtractor = MapNode.<String, DocumentExtraction>builder()
	            .mapWith((documentText, ctx) -> {
	                // The lambda is the batch work unit: build a prompt, call the extractor,
	                // and return one structured extraction per input document.
	                String prompt = "Extract structured fields from this document.\n" +
	                        "Use \"unknown\" for absent fields. Use [] for absent lists.\n\n" +
	                        "Document:\n" + documentText;
	                return extractor.process(prompt, ctx);
	            })
	            .timeout(180, TimeUnit.SECONDS)
	            .maxInFlight(3)
	            .continueOnError()
	            .build("batchExtractor");

	    // GatherNode collects the successful batch results and turns them into a clean list.
	    GatherNode gatherExtractions = GatherNode.builder()
	            .collectMode(CollectionMode.SUCCESS_ONLY)
	            .aggregate(GatherNode.Aggregation.LIST)
	            .build("gatherExtractions");

	    // Second batch pass: every extracted document is scored for operational risk.
	    MapNode<DocumentExtraction, RiskAssessment> batchRisk = MapNode.<DocumentExtraction, RiskAssessment>builder()
	            .mapWith((extraction, ctx) -> {
	                String prompt = """
	                		Assess this extracted document for operational risk.
	                		riskLevel must be LOW, MEDIUM, or HIGH.
	                		Flag missing due dates, missing totals, unusual obligations,
	                		unclear counterparties, or anything requiring human review.
	                		Missing PO numbers, addresses, or tax details should raise risk to MEDIUM.
                    
	                		Extraction:
	                		""" + extraction;
	                return riskNode.process(prompt, ctx);
	            })
	            .timeout(180, TimeUnit.SECONDS)
	            .maxInFlight(3)
	            .continueOnError()
	            .build("batchRisk");

	    // GatherNode — collect risk assessments
	    GatherNode gatherRisks = GatherNode.builder()
	            .collectMode(CollectionMode.SUCCESS_ONLY)
	            .aggregate(GatherNode.Aggregation.LIST)
	            .build("gatherRisks");

	    // Final report nodes are plain LLM nodes, wrapped in the shared chain for retries/backoff.
	    LLMNode batchReportLlm = LLMNode.builder()
	            .model(chain)
	            .memory("batch-extraction")
	            .build();

	    LLMNode failureLlm = LLMNode.builder()
	            .model(chain)
	            .memory("batch-extraction")
	            .build();

	    // Graph flow:
	    // start -> batchExtractor -> gatherExtractions -> unwrapExtractions -> batchRisk
	    // batchRisk -> gatherRisks -> batchReportPrompt -> batchReport
	    // any failure -> failurePrompt -> failureReport
	    return GraphBuilder.named("batch-document-extraction")
	            .addNode("start", new CaptureDocumentsNode())
	            .addNode("batchExtractor", batchExtractor)
	            .addNode("gatherExtractions", gatherExtractions)
	            .addNode("unwrapExtractions", new UnwrapExtractionsNode())
	            .addNode("batchRisk", batchRisk)
	            .addNode("gatherRisks", gatherRisks)
	            .addNode("batchReportPrompt", new BatchReportPromptNode())
	            .addNode("batchReport", batchReportLlm)
	            .addNode("failurePrompt", new FailurePromptNode())
	            .addNode("failureReport", failureLlm)
	            .connect("start", "batchExtractor")
	            .connect("batchExtractor", "gatherExtractions")
	            .connect("gatherExtractions", "unwrapExtractions")
	            .connect("unwrapExtractions", "batchRisk")
	            .connect("batchRisk", "gatherRisks")
	            .connect("gatherRisks", "batchReportPrompt")
	            .connect("batchReportPrompt", "batchReport")
	            .connect("failurePrompt", "failureReport")
	            .connectAnyFailureTo("failurePrompt")
	            .build();
	}
	
	public static ChatModel geminiModel(String apiKey, String modelName) {
		ChatModel model = LLM.gemini(modelName, apiKey);
		if(model instanceof GeminiChatModel gemini) {
			return gemini.withTemperature(0.0).withMaxTokens(4096);
		}
		return model;
	}
	
	private static String requireEnv(String name) {
	    String value = System.getenv(name);
	    if (value == null || value.isBlank()) {
	        throw new IllegalStateException(
	                name + " is required.\n" +
	                "Get your free Gemini API key from: https://aistudio.google.com/apikey\n"
	        );
	    }
	    return value;
	}
	
	private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
	
	private static List<String> sampleDocuments() {
	    return List.of(
	        sampleInvoice(),      
	        sampleContract(),     
	        sampleReceipt()       
	    );
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

	private static String sampleContract() {
	    return """
	            SERVICE AGREEMENT
	            Between: TechSolutions Ltd (Provider)
	            And: RetailCorp Inc (Client)
	            Contract No: SC-2026-089
	            Effective Date: 2026-06-01
	            End Date: 2026-12-31

	            Services: Monthly software maintenance and support
	            Monthly Fee: USD 5,000
	            Payment Terms: Net 30 days
	            """;
	}

	private static String sampleReceipt() {
	    return """
	            RECEIPT
	            Store: QuickMart Supplies
	            Receipt No: REC-2026-1123
	            Date: 2026-05-30

	            Items:
	            - Office supplies: USD 150
	            - Printer paper: USD 80

	            Total Paid: USD 230
	            Payment Method: Credit Card
	            """;
	}

	// New capture node for list of documents
	static final class CaptureDocumentsNode implements NodePlugin<List<String>, List<String>> {
	    @Override
	    public List<String> process(List<String> input, NodeContext context) {
	        // Preserve the original batch input in context for debugging and downstream reuse.
	        context.set("documents", input);
	        context.getLogger().info("[CaptureDocuments] Received " + input.size() + " documents");
	        return input;
	    }
	}
	
	static final class BatchReportPromptNode implements NodePlugin<Object, String> {
	    private static final PromptTemplate TEMPLATE = PromptTemplate.of(
	            """
	            Create a batch document processing report.
	            Include a summary per document, overall risk levels, and recommended actions.
	            Flag any HIGH risk documents immediately.

	            Batch results:
	            {{results}}
	            """,
	            Variable.required("results")
	    );

	    @Override
	    public String process(Object input, NodeContext context) {
	        // GatherNode produces a batch-shaped result; this prompt turns it into one report.
	        GatherNode.GatherResult risks = (GatherNode.GatherResult) input;
	        return TEMPLATE.render("results", risks.items());
	    }
	}

	static final class FailurePromptNode implements NodePlugin<Object, String> {
	    private static final PromptTemplate TEMPLATE = PromptTemplate.of(
	            """
	            A batch document extraction graph node failed.
	            Explain what failed, what partial data may still be usable,
	            and what a human should do next.

	            Failure object:
	            {{failure}}
	            """,
	            Variable.required("failure")
	    );

	    @Override
	    public String process(Object input, NodeContext context) {
	        // The failure path stays separate from the success path so the graph can explain errors clearly.
	        return TEMPLATE.render("failure", input);
	    }
	}
	
	/* This is actually a common pattern when using GatherNode
	 * you always need to unwrap the GatherResult before passing 
	 * to a node that expects the raw items.
	 */
	static final class UnwrapExtractionsNode implements NodePlugin<GatherNode.GatherResult, List<DocumentExtraction>> {
	    @Override
	    @SuppressWarnings("unchecked")
	    public List<DocumentExtraction> process(GatherNode.GatherResult input, NodeContext context) {
	        // Unwrap GatherResult back to a plain list so the next MapNode receives raw typed records.
	        List<DocumentExtraction> extractions = input.items();
	        context.getLogger().info("[UnwrapExtractions] Unwrapped " + extractions.size() + " extractions");
	        return extractions;
	    }
	}
}