package tutorials.modes;

import io.oxyjen.execution.gather.CollectionMode;

/**
 * Modes tutorial 2:
 * GatherNode collection modes.
 */
final class CollectionModesTutorial {

    private CollectionModesTutorial() {}

    public static void main(String[] args) {
        for (CollectionMode mode : CollectionMode.values()) {
            System.out.println(mode);
        }
    }
}
