package io.oxyjen.persist.json;

import io.oxyjen.execution.ExecutionRecord;

public interface ExecutionRecordSerializer {

    String serialize(ExecutionRecord record);

    ExecutionRecord deserialize(String json);
}