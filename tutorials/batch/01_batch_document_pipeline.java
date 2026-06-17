package tutorials.batch;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.MapNode;
import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.llm.schema.SchemaGenerator;
import io.oxyjen.llm.schema.SchemaNode;
import io.oxyjen.resilience.ratelimit.RateLimiter;
import io.oxyjen.resilience.ratelimit.RateLimiters;

/**
 * Batch tutorial 1:
 * Parallel batch document processing with rate limiting.
 */
final class BatchDocumentPipelineTutorial {

    public record Extraction(String title, String vendor, String totalDue) {}
    public record Risk(String riskLevel, String reason) {}

    private BatchDocumentPipelineTutorial() {}

    public static void main(String[] args) {
        RateLimiter limiter = RateLimiters.geminiFreeTier();
        ChatModel model = LLM.withRateLimit(LLM.gemini("gemini/gemini-flash-latest"), limiter);
        ChatModel chain = LLMChain.builder()
            .primary(model)
            .retry(3)
            .timeout(java.time.Duration.ofSeconds(45))
            .build();

        SchemaNode<Extraction> extractor = SchemaNode.builder(Extraction.class)
            .model(chain)
            .schema(SchemaGenerator.fromClass(Extraction.class))
            .maxRetries(3)
            .build();

        SchemaNode<Risk> riskNode = SchemaNode.builder(Risk.class)
            .model(chain)
            .schema(SchemaGenerator.fromClass(Risk.class))
            .maxRetries(2)
            .build();

        MapNode<String, Extraction> extractMap = MapNode.<String, Extraction>builder()
            .mapWith((text, ctx) -> extractor.process("Extract fields from:\n" + text, ctx))
            .maxInFlight(3)
            .timeout(180, TimeUnit.SECONDS)
            .continueOnError()
            .build("extract");

        GatherNode gather = GatherNode.builder()
            .collectMode(CollectionMode.SUCCESS_ONLY)
            .aggregate(GatherNode.Aggregation.LIST)
            .build("gather");

        ExecutionRuntime runtime = ExecutionRuntime.builder()
            .maxConcurrency(3)
            .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
            .defaultTimeout(300, TimeUnit.SECONDS)
            .build();

        List<String> docs = List.of("invoice text", "contract text", "receipt text");
        var result = extractMap.process(docs, new NodeContext());
        System.out.println(result.toSuccessfulList());
        System.out.println(riskNode.getClass().getSimpleName());
        System.out.println(gather.getClass().getSimpleName());
        System.out.println(runtime.getFailureMode());
    }
}
