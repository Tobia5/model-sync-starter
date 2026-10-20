package io.modelsync.core;

import java.util.Objects;
import java.util.function.Function;
/** Explicit route definition. Reusing a DTO does not imply sharing its authority or tenant scope. */
public final class ModelBinding<T> {
    private final String id, boundary, ownerSystem, quota;
    private final Class<T> modelType;
    private final OwnerPort<T> owner;
    private final ProjectionStore<T> projection;
    private final Function<T,String> digest;
    private final LocalRole role;
    private final FreshnessPolicy freshness;
    private final boolean repair;
    private ModelBinding(Builder<T> b) {
        id=SubjectKey.checked(b.id,"binding",128); boundary=SubjectKey.checked(b.boundary,"boundary",128);
        ownerSystem=SubjectKey.checked(b.ownerSystem,"ownerSystem",128); quota=SubjectKey.checked(b.quota,"quota",128);
        modelType=Objects.requireNonNull(b.type); owner=Objects.requireNonNull(b.owner,"owner");
        projection=Objects.requireNonNull(b.projection,"projection"); digest=Objects.requireNonNull(b.digest,"digest");
        role=Objects.requireNonNull(b.role); freshness=Objects.requireNonNull(b.freshness); repair=b.repair;
    }
    public static <T> Builder<T> builder(String id, Class<T> type) { return new Builder<>(id,type); }
    public String id(){return id;} public String boundary(){return boundary;}
    public String ownerSystem(){return ownerSystem;} public String quota(){return quota;}
    public Class<T> modelType(){return modelType;} public OwnerPort<T> owner(){return owner;}
    public ProjectionStore<T> projection(){return projection;} public LocalRole role(){return role;}
    public FreshnessPolicy freshness(){return freshness;} public boolean repair(){return repair;}
    public String digest(T value){return SubjectKey.checked(digest.apply(value),"digest",256);}
    public static final class Builder<T> {
        private final String id; private final Class<T> type;
        private String boundary, ownerSystem, quota;
        private OwnerPort<T> owner; private ProjectionStore<T> projection; private Function<T,String> digest;
        private LocalRole role=LocalRole.REPLICA; private FreshnessPolicy freshness=FreshnessPolicy.defaults();
        private boolean repair;
        private Builder(String id, Class<T> type){this.id=id;this.type=type;}
        public Builder<T> boundary(String value){boundary=value;return this;}
        public Builder<T> owner(String system, BlockingOwner<T> port){ownerSystem=system;owner=port;return this;}
        public Builder<T> asyncOwner(String system, AsyncOwner<T> port){ownerSystem=system;owner=port;return this;}
        public Builder<T> replica(ProjectionStore<T> store){role=LocalRole.REPLICA;projection=store;return this;}
        public Builder<T> cache(ProjectionStore<T> store){role=LocalRole.CACHE;projection=store;return this;}
        public Builder<T> fingerprint(Function<T,String> function){digest=function;return this;}
        public Builder<T> quota(String value){quota=value;return this;}
        public Builder<T> freshness(FreshnessPolicy value){freshness=value;return this;}
        /** Opt in only for a projection whose mapped fields are all owned by this source. */
        public Builder<T> repairFromOwner(){repair=true;return this;}
        public ModelBinding<T> build(){return new ModelBinding<>(this);}
    }
}
