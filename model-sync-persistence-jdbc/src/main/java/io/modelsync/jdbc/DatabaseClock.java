package io.modelsync.jdbc;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
/** Database wall clock, avoiding quota/lease decisions based on different pod clocks. */
public final class DatabaseClock extends Clock {
    private final JdbcTemplate jdbc;private final ZoneId zone;
    public DatabaseClock(JdbcTemplate jdbc){this(jdbc,ZoneOffset.UTC);}
    private DatabaseClock(JdbcTemplate jdbc,ZoneId zone){this.jdbc=jdbc;this.zone=zone;}
    @Override public ZoneId getZone(){return zone;}
    @Override public Clock withZone(ZoneId zone){return new DatabaseClock(jdbc,zone);}
    @Override public Instant instant(){
        // Oracle and H2 both support DUAL. JDBC driver must support OffsetDateTime conversion.
        return jdbc.queryForObject("select current_timestamp from dual",(rs,row)->rs.getObject(1,OffsetDateTime.class).toInstant());
    }
}
