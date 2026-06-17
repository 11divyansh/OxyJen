package examples;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;

public class SimpleCall {

	public static void main(String[] args) {
		ChatModel model = LLM.gemini("gemini/gemini-flash-latest");
		System.out.println(model.chat("Say hello"));
	}
}
