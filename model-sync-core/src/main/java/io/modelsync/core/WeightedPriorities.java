package io.modelsync.core;

import java.util.concurrent.atomic.AtomicInteger;
/** Work-conserving weighted selection. Fair admission, not cancellation of in-flight HTTP requests. */
public final class WeightedPriorities {
    private final Priority[] wheel;
    private final AtomicInteger cursor=new AtomicInteger();
    public WeightedPriorities(int urgent,int high,int normal,int background){
        int[] w={urgent,high,normal,background};
        for(int n:w) if(n<1 || n>1000) throw new IllegalArgumentException("Weight must be 1..1000");
        wheel=new Priority[urgent+high+normal+background]; int p=0;
        for(int i=0;i<w.length;i++) for(int j=0;j<w[i];j++) wheel[p++]=Priority.values()[i];
    }
    public Priority next(){return wheel[Math.floorMod(cursor.getAndIncrement(),wheel.length)];}
    public static WeightedPriorities defaults(){return new WeightedPriorities(4,3,2,1);}
}
