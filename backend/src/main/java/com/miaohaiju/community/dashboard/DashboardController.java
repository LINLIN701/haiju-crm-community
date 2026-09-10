package com.miaohaiju.community.dashboard;

import com.miaohaiju.community.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/overview")
    ApiResponse<Overview> overview() {
        Long customerCount = jdbc.queryForObject("SELECT COUNT(*) FROM customers", Long.class);
        Long dueFollowUps = jdbc.queryForObject("""
                SELECT COUNT(*) FROM contact_records r
                WHERE r.next_contact_at IS NOT NULL AND r.next_contact_at <= CURRENT_TIMESTAMP
                  AND r.id = (SELECT MAX(r2.id) FROM contact_records r2 WHERE r2.customer_id = r.customer_id)
                """, Long.class);
        BigDecimal totalConsumption = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM consumptions", BigDecimal.class);
        return ApiResponse.ok(new Overview(customerCount == null ? 0 : customerCount,
                dueFollowUps == null ? 0 : dueFollowUps,
                totalConsumption == null ? BigDecimal.ZERO : totalConsumption));
    }

    public record Overview(long customerCount, long dueFollowUps, BigDecimal totalConsumption) {
    }
}
