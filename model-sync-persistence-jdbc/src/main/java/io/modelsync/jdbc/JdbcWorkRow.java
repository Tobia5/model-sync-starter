package io.modelsync.jdbc;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public record JdbcWorkRow(
    WorkKey key,
    int priority,
    long generation,
    int attempts,
    boolean force,
    String status,
    String token,
    Long until,
    long nextAt,
    String lastCode) {}
