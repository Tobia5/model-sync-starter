package io.modelsync.core;

import java.util.List;

public record BoundaryPage(String snapshotId, List<String> subjectIds, String nextCursor, boolean complete) {
  public BoundaryPage {
    subjectIds = List.copyOf(subjectIds);
  }
}
