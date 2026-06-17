package tutorials.tools;

import java.util.Map;
import java.util.Set;

import io.oxyjen.core.NodeContext;
import io.oxyjen.tools.ToolCall;
import io.oxyjen.tools.ToolNode;
import io.oxyjen.tools.ToolResult;
import io.oxyjen.tools.builtin.file_read.FileReaderTool;
import io.oxyjen.tools.builtin.http.HttpTool;
import io.oxyjen.tools.safety.ToolSandbox;

/**
 * Tools tutorial 1:
 * File and HTTP tools with sandboxing.
 */
final class FileAndHttpToolsTutorial {

    private FileAndHttpToolsTutorial() {}

    public static void main(String[] args) {
        ToolSandbox sandbox = ToolSandbox.builder()
            .allowedDirectory("D:/OxyJen-v0.1/oxyjen-core")
            .timeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .strictMode(true)
            .build();

        ToolNode toolNode = new ToolNode(
            "ops",
            new FileReaderTool(sandbox),
            new HttpTool(Set.of("example.com"), 10_000, 20_000)
        );

        ToolResult fileRead = toolNode.process(
            ToolCall.of("file_read", Map.of("path", "README.md")),
            new NodeContext()
        );

        System.out.println(fileRead);
    }
}
