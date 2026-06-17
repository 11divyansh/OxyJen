package tutorials.easy;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;

/**
 * Easy tutorial 1:
 * Send one prompt to one model.
 */
final class HelloWorldTutorial {

    private HelloWorldTutorial() {}

    public static void main(String[] args) {
        ChatModel model = LLM.gemini("gemini/gemini-flash-latest");
        String response = model.chat("Say hello in one sentence.");
        System.out.println(response);
    }
}
