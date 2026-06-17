package tutorials.modes;

import io.oxyjen.tools.safety.ToolSandbox;

/**
 * Modes tutorial 5:
 * Basic and strict tool sandboxing.
 */
final class ToolSandboxModesTutorial {

    private ToolSandboxModesTutorial() {}

    public static void main(String[] args) {
        ToolSandbox basic = ToolSandbox.basic();
        ToolSandbox strict = ToolSandbox.strict();
        ToolSandbox custom = ToolSandbox.builder()
            .allowedDirectory("D:/OxyJen-v0.1/oxyjen-core")
            .timeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .strictMode(true)
            .build();

        System.out.println("basic timeout=" + basic.getTimeoutMs());
        System.out.println("strict mode=" + strict.isStrictMode());
        System.out.println("custom dirs=" + custom.getAllowedDirectories());
    }
}
