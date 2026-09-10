package com.miaohaiju.community.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OperationLogService {
    private final JdbcTemplate jdbc;

    public OperationLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String action, String targetType, Long targetId, String detail) {
        jdbc.update("INSERT INTO operation_logs(action, target_type, target_id, detail) VALUES (?, ?, ?, ?)",
                action, targetType, targetId, detail);
    }

    public List<OperationLogItem> latest() {
        return jdbc.query("""
                SELECT id, action, target_type, target_id, detail, created_at
                FROM operation_logs ORDER BY id DESC LIMIT 200
                """, (rs, rowNum) -> new OperationLogItem(
                rs.getLong("id"), rs.getString("action"), rs.getString("target_type"),
                rs.getObject("target_id", Long.class), rs.getString("detail"),
                timestamp(rs.getTimestamp("created_at"))));
    }

    private LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record OperationLogItem(Long id, String action, String targetType, Long targetId,
                                   String detail, LocalDateTime createdAt) {
    }
}
