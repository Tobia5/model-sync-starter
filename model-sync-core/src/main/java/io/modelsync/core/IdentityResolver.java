package io.modelsync.core;

import java.util.List;
/** Application-specific IBM MDM mapping seam; never guess a canonical ID from an ambiguous match. */
@FunctionalInterface
public interface IdentityResolver {
    Resolution resolve(String sourceSystem,SubjectKey sourceKey);
    record Resolution(Status status,String canonicalId,List<String> aliases,String mappingVersion){
        public enum Status { RESOLVED, UNRESOLVED, AMBIGUOUS }
        public Resolution { aliases=List.copyOf(aliases); }
    }
}
