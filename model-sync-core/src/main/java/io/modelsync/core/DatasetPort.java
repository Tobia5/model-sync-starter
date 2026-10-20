package io.modelsync.core;

import java.math.BigDecimal;
import java.util.Map;
/** Vista/DWH protocol seam, distinct from operational record reconciliation. */
public final class DatasetPort {
    private DatasetPort(){}
    public record Slice(String dataset,String tenant,String snapshot,String partition,String mappingVersion,String grain){
        public Slice {
            for(String s:new String[]{dataset,tenant,snapshot,partition,mappingVersion,grain})
                if(s==null || s.isBlank())throw new IllegalArgumentException("Dataset identity fields must be explicit");
        }
    }
    public record Manifest(Slice slice,long rows,long unresolved,Map<String,BigDecimal> totals,String digest){
        public Manifest{java.util.Objects.requireNonNull(slice);totals=Map.copyOf(totals);if(rows<0||unresolved<0)throw new IllegalArgumentException("Negative count");}
    }
    @FunctionalInterface public interface Reader { Manifest inspect(Slice slice); }
    @FunctionalInterface public interface Sink { Receipt publish(Slice slice,String idempotencyKey,byte[] payload); }
    public record Receipt(String receiptId,Stage stage){ public enum Stage { ACCEPTED, APPLIED, VERIFIED } }
    public enum Comparison { MATCH, DIFFERENT, INCOMPLETE, INCOMPARABLE }
    public static Comparison compare(Manifest expected,Manifest actual){
        if(!expected.slice().equals(actual.slice()))return Comparison.INCOMPARABLE;
        if(expected.unresolved()!=0 || actual.unresolved()!=0)return Comparison.INCOMPLETE;
        if(expected.rows()!=actual.rows() || !expected.totals().keySet().equals(actual.totals().keySet()))return Comparison.DIFFERENT;
        for(String key:expected.totals().keySet())
            if(expected.totals().get(key).compareTo(actual.totals().get(key))!=0)return Comparison.DIFFERENT;
        if(expected.digest()==null || actual.digest()==null || expected.digest().isBlank() || actual.digest().isBlank())return Comparison.INCOMPLETE;
        return expected.digest().equals(actual.digest()) ? Comparison.MATCH : Comparison.DIFFERENT;
    }
}
