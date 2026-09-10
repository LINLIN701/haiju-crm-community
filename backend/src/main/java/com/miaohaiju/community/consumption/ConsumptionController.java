package com.miaohaiju.community.consumption;

import com.miaohaiju.community.audit.OperationLogService;
import com.miaohaiju.community.common.ApiResponse;
import com.miaohaiju.community.customer.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/consumptions")
public class ConsumptionController {
    private final JdbcTemplate jdbc;
    private final CustomerService customers;
    private final OperationLogService operationLogs;

    public ConsumptionController(JdbcTemplate jdbc, CustomerService customers, OperationLogService operationLogs) {
        this.jdbc = jdbc;
        this.customers = customers;
        this.operationLogs = operationLogs;
    }

    @GetMapping
    ApiResponse<List<ConsumptionView>> list(@PathVariable long customerId) {
        customers.get(customerId);
        return ApiResponse.ok(jdbc.query("""
                SELECT id, customer_id, occurred_at, amount, item_name, note, created_at
                FROM consumptions WHERE customer_id = ? ORDER BY occurred_at DESC, id DESC
                """, (rs, rowNum) -> map(rs), customerId));
    }

    @PostMapping
    ApiResponse<ConsumptionView> create(@PathVariable long customerId, @Valid @RequestBody ConsumptionRequest request) {
        customers.get(customerId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO consumptions(customer_id, occurred_at, amount, item_name, note)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, customerId);
            statement.setTimestamp(2, Timestamp.valueOf(request.occurredAt()));
            statement.setBigDecimal(3, request.amount());
            statement.setString(4, request.itemName().trim());
            statement.setString(5, request.note() == null ? null : request.note().trim());
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        operationLogs.record("CONSUMPTION_RECORDED", "CUSTOMER", customerId,
                "记录消费：" + request.itemName().trim() + "，金额 " + request.amount());
        return ApiResponse.ok(jdbc.query("""
                SELECT id, customer_id, occurred_at, amount, item_name, note, created_at
                FROM consumptions WHERE id = ?
                """, (rs, rowNum) -> map(rs), id).get(0));
    }

    private ConsumptionView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConsumptionView(rs.getLong("id"), rs.getLong("customer_id"),
                rs.getTimestamp("occurred_at").toLocalDateTime(), rs.getBigDecimal("amount"),
                rs.getString("item_name"), rs.getString("note"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    public record ConsumptionRequest(
            @NotNull LocalDateTime occurredAt,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @NotBlank @Size(max = 200) String itemName,
            @Size(max = 1000) String note) {
    }

    public record ConsumptionView(Long id, Long customerId, LocalDateTime occurredAt,
                                  BigDecimal amount, String itemName, String note, LocalDateTime createdAt) {
    }
}
