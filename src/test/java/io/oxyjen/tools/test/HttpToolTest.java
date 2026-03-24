package io.oxyjen.tools.test;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.tools.ToolResult;
import io.oxyjen.tools.builtin.HttpTool;

class HttpToolTest {

	@Test
	void testHttpToolGetRequest() {
		HttpTool httpTool = HttpTool.builder()
				.allowDomain("jsonplaceholder.typicode.com")
				.timeout(10000)
				.maxResponseSize(100000)
				.build();
		Map<String, Object> input = new HashMap<>();
		input.put("method", "GET");
		input.put("url", "https://jsonplaceholder.typicode.com/posts");

		Map<String, String> query = Map.of(
		        "userId", "1"
		);
		input.put("query", query);
		NodeContext context = new NodeContext();
		ToolResult result = httpTool.execute(input, context);
		out.println(result);
		assertTrue(result.isSuccess());
		out.println(result.getOutput());
	}
	
	@Test
	void testHttpToolGithubIssueCreation() {
		HttpTool httpTool = HttpTool.builder()
				.allowDomain("api.github.com")
				.timeout(5000)
				.maxResponseSize(100000)
				.build();
		String token = System.getenv("GITHUB_TOKEN");
		Map<String, Object> input = new HashMap<>();
		input.put("method", "POST");
		input.put("url", "https://api.github.com/repos/11divyansh/EventManagementSystemAPI/issues");
		input.put("headers", Map.of(
		    "Authorization", "Bearer " + token,
		    "Accept", "application/vnd.github+json",
		    "Content-Type", "application/json"
		));

		input.put("body", """
		{
		  "title": "Test HttpTool issue creation",
		  "body": "Something broke in production"
		}
		""");
		NodeContext context = new NodeContext();
		ToolResult result = httpTool.execute(input, context);
		assertTrue(result.isSuccess());
	}
}
