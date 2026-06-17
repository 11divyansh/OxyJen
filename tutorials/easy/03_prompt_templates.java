package tutorials.easy;

import io.oxyjen.llm.prompts.PromptTemplate;
import io.oxyjen.llm.prompts.Variable;

/**
 * Easy tutorial 3:
 * Prompt templates with variables.
 */
final class PromptTemplatesTutorial {

    private PromptTemplatesTutorial() {}

    public static void main(String[] args) {
        PromptTemplate template = PromptTemplate.of(
            """
            Summarize this text in 3 bullet points:
            {{text}}
            """,
            Variable.required("text")
        );

        String prompt = template.render("text", "Oxyjen helps build graph-based AI workflows.");
        System.out.println(prompt);
    }
}
