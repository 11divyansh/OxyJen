package io.oxyjen.tools.builtin.file_read;

import java.util.Optional;

/**
 * Use this record to get arguments from ToolCall
 * Usage: FileReadRequest req = toolCall.getInputAs(FileReadRequest.class);
 * req.path();
 * req.encoding();
 */
public record FileReadRequest(
	    String path,                  // required
	    String encoding,              // null if not sent
	    Optional<Long> offset,
	    Optional<Long> limit,
	    Optional<Integer> lineStart,
	    Optional<Integer> lineEnd,
	    Optional<Integer> chunkSize,
	    Optional<Integer> chunkIndex,
	    Optional<Boolean> metadataOnly,
	    Optional<Boolean> binaryMode,
	    Optional<Integer> maxLines
	) {}