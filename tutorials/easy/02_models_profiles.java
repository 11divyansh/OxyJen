package tutorials.easy;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;

/**
 * Easy tutorial 2:
 * Provider facade and profiles.
 */
final class ModelsAndProfilesTutorial {

    private ModelsAndProfilesTutorial() {}

    public static void main(String[] args) {
        ChatModel fast = LLM.profile("fast");
        ChatModel smart = LLM.profile("smart");

        System.out.println("fast => " + fast.getClass().getSimpleName());
        System.out.println("smart => " + smart.getClass().getSimpleName());
    }
}
