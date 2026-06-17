package tutorials.modes;

import java.util.concurrent.TimeUnit;

import io.oxyjen.execution.ExecutionRuntime;

/**
 * Modes tutorial 1:
 * ExecutionRuntime failure modes.
 */
final class ExecutionRuntimeModesTutorial {

    private ExecutionRuntimeModesTutorial() {}

    public static void main(String[] args) {
        ExecutionRuntime failFast = ExecutionRuntime.builder()
            .maxConcurrency(4)
            .failureMode(ExecutionRuntime.FailureMode.FAIL_FAST)
            .defaultTimeout(30, TimeUnit.SECONDS)
            .build();

        ExecutionRuntime collectErrors = ExecutionRuntime.builder()
            .maxConcurrency(4)
            .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
            .defaultTimeout(30, TimeUnit.SECONDS)
            .build();

        ExecutionRuntime skipFailed = ExecutionRuntime.builder()
            .maxConcurrency(4)
            .failureMode(ExecutionRuntime.FailureMode.SKIP_FAILED)
            .defaultTimeout(30, TimeUnit.SECONDS)
            .build();

        System.out.println(failFast.getFailureMode());
        System.out.println(collectErrors.getFailureMode());
        System.out.println(skipFailed.getFailureMode());
    }
}
