package com.miaohaiju.community.contact;

import com.miaohaiju.community.audit.OperationLogService;
import com.miaohaiju.community.common.ApiResponse;
import com.miaohaiju.community.common.BusinessException;
import com.miaohaiju.community.customer.CustomerService;
import jakarta.validation.Valid;
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

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/contacts")
public class ContactController {
    private final JdbcTemplate jdbc;
    private final CustomerService customers;
    private final OperationLogService operationLogs;

    public ContactController(JdbcTemplate jdbc, CustomerService customers, OperationLogService operationLogs) {
        this.jdbc = jdbc;
        this.customers = customers;
        this.operationLogs = operationLogs;
    }

    @GetMapping
    ApiResponse<List<ContactView>> list(@PathVariable long customerId) {
        customers.get(customerId);
        return ApiResponse.ok(jdbc.query("""
                SELECT id, customer_id, contact_at, channel, summary, next_contact_at, created_at
                FROM contact_records WHERE customer_id = ? ORDER BY contact_at DESC, id DESC
                """, (rs, rowNum) -> new ContactView(
                rs.getLong("id"), rs.getLong("customer_id"),
                toTime(rs.getTimestamp("contact_at")), rs.getString("channel"), rs.getString("summary"),
                toTime(rs.getTimestamp("next_contact_at")), toTime(rs.getTimestamp("created_at"))), customerId));
    }

    @PostMapping
    ApiResponse<ContactView> create(@PathVariable long customerId, @Valid @RequestBody ContactRequest request) {
        customers.get(customerId);
        if (request.nextContactAt() != null && request.nextContactAt().isBefore(request.contactAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40021, "下次跟进时间不能早于本次跟进时间");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO contact_records(customer_id, contact_at, channel, summary, next_contact_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, customerId);
            statement.setTimestamp(2, Timestamp.valueOf(request.contactAt()));
            statement.setString(3, request.channel().trim());
            statement.setString(4, request.summary().trim());
            statement.setTimestamp(5, request.nextContactAt() == null ? null : Timestamp.valueOf(request.nextContactAt()));
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        operationLogs.record("CONTACT_RECORDED", "CUSTOMER", customerId, "记录客户跟进：" + request.channel().trim());
        return ApiResponse.ok(find(id));
    }

    private ContactView find(long id) {
        return jdbc.query("""
                SELECT id, customer_id, contact_at, channel, summary, next_contact_at, created_at
                FROM contact_records WHERE id = ?
                """, (rs, rowNum) -> new ContactView(
                rs.getLong("id"), rs.getLong("customer_id"),
                toTime(rs.getTimestamp("contact_at")), rs.getString("channel"), rs.getString("summary"),
                toTime(rs.getTimestamp("next_contact_at")), toTime(rs.getTimestamp("created_at"))), id).get(0);
    }

    private static LocalDateTime toTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record ContactRequest(
            @NotNull LocalDateTime contactAt,
            @NotBlank @Size(max = 30) String channel,
            @NotBlank @Size(max = 1000) String summary,
            LocalDateTime nextContactAt) {
    }

    public record ContactView(Long id, Long customerId, LocalDateTime contactAt, String channel,
                              String summary, LocalDateTime nextContactAt, LocalDateTime createdAt) {
    }
}
