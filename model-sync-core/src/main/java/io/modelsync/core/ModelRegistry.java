package io.modelsync.core;

import java.util.*;
public final class ModelRegistry {
    private final Map<String,ModelBinding<?>> bindings;
    public ModelRegistry(Collection<? extends ModelBinding<?>> definitions) {
        Map<String,ModelBinding<?>> all=new LinkedHashMap<>();
        for (ModelBinding<?> b:definitions)
            if(all.putIfAbsent(b.id(),b)!=null) throw new IllegalArgumentException("Duplicate binding: "+b.id());
        bindings=Collections.unmodifiableMap(all);
    }
    public ModelBinding<?> get(String id) {
        ModelBinding<?> b=bindings.get(id);
        if(b==null) throw new IllegalArgumentException("Unknown binding: "+id); return b;
    }
    public <T> ModelBinding<T> get(String id,Class<T> type) {
        ModelBinding<?> b=get(id);
        if(!b.modelType().equals(type)) throw new IllegalArgumentException("Model type mismatch");
        @SuppressWarnings("unchecked") ModelBinding<T> result=(ModelBinding<T>)b; return result;
    }
    public <T> ModelBinding<T> get(Class<T> type) {
        List<ModelBinding<?>> found=bindings.values().stream().filter(b->b.modelType().equals(type)).toList();
        if(found.size()!=1) throw new IllegalArgumentException("Use binding ID: expected one route for "+type.getName()+", got "+found.size());
        return get(found.getFirst().id(),type);
    }
    public Collection<ModelBinding<?>> all(){return bindings.values();}
    public List<ModelBinding<?>> boundary(String id) {
        return bindings.values().stream().filter(b->b.boundary().equals(id)).toList();
    }
}
