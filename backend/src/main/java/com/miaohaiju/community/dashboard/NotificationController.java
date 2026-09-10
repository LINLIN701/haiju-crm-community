package com.miaohaiju.community.dashboard;

import com.miaohaiju.community.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final JdbcTemplate jdbc;

    public NotificationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    ApiResponse<List<FollowUpReminder>> reminders() {
        return ApiResponse.ok(jdbc.query("""
                SELECT c.id customer_id, c.name customer_name, r.next_contact_at, r.summary
                FROM contact_records r
                JOIN customers c ON c.id = r.customer_id
                WHERE r.next_contact_at IS NOT NULL AND r.next_contact_at <= CURRENT_TIMESTAMP
                  AND r.id = (SELECT MAX(r2.id) FROM contact_records r2 WHERE r2.customer_id = r.customer_id)
                ORDER BY r.next_contact_at ASC LIMIT 200
                """, (rs, rowNum) -> new FollowUpReminder(
                rs.getLong("customer_id"), rs.getString("customer_name"),
                timestamp(rs.getTimestamp("next_contact_at")), rs.getString("summary"))));
    }

    private LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record FollowUpReminder(Long customerId, String customerName, LocalDateTime dueAt, String lastSummary) {
    }
}
