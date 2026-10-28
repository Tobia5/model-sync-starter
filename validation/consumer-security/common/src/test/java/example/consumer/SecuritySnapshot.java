package example.consumer;

import java.util.List;
import java.util.Map;

/** Fresh observer reads are verification, separate from protected application payload access. */
public record SecuritySnapshot(AccessCounts access, int mappingReads, int ownerCalls, int events,
    List<Map<String,Object>> rows) {}
