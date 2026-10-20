package io.modelsync.core;

import java.util.List;
/** Optional owner capability. Absence means reconciliation covers known IDs only. */
public interface BoundaryInventory {
    List<ModelDescriptor> models();
    Page enumerate(String model,String tenant,String cursor,int limit);
    record ModelDescriptor(String id,String schemaVersion){}
    record Page(String snapshotId,List<String> subjectIds,String nextCursor,boolean complete){
        public Page { subjectIds=List.copyOf(subjectIds); }
    }
}
