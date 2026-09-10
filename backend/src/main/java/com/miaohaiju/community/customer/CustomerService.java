package com.miaohaiju.community.customer;

import com.miaohaiju.community.audit.OperationLogService;
import com.miaohaiju.community.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.miaohaiju.community.customer.CustomerDtos.CustomerRequest;
import static com.miaohaiju.community.customer.CustomerDtos.CustomerView;
import static com.miaohaiju.community.customer.CustomerDtos.ImportResult;

@Service
public class CustomerService {
    private final JdbcTemplate jdbc;
    private final OperationLogService operationLogs;

    public CustomerService(JdbcTemplate jdbc, OperationLogService operationLogs) {
        this.jdbc = jdbc;
        this.operationLogs = operationLogs;
    }

    public List<CustomerView> list(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(normalized)) {
            return jdbc.query("SELECT * FROM customers ORDER BY id DESC LIMIT 500", this::map);
        }
        String like = "%" + normalized + "%";
        return jdbc.query("""
                SELECT * FROM customers
                WHERE name LIKE ? OR phone LIKE ? OR wechat LIKE ? OR tags LIKE ?
                ORDER BY id DESC LIMIT 500
                """, this::map, like, like, like, like);
    }

    public CustomerView get(long id) {
        return jdbc.query("SELECT * FROM customers WHERE id = ?", this::map, id).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, 40401, "客户不存在"));
    }

    @Transactional
    public CustomerView create(CustomerRequest request) {
        rejectDuplicatePhone(request.phone(), null);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO customers(name, phone, wechat, tags, notes) VALUES (?, ?, ?, ?, ?)
                        """, new String[]{"id"});
                statement.setString(1, clean(request.name()));
                statement.setString(2, nullable(request.phone()));
                statement.setString(3, nullable(request.wechat()));
                statement.setString(4, nullable(request.tags()));
                statement.setString(5, nullable(request.notes()));
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "该手机号已存在，请先核对客户资料");
        }
        long id = keyHolder.getKey().longValue();
        operationLogs.record("CUSTOMER_CREATED", "CUSTOMER", id, "新增客户：" + clean(request.name()));
        return get(id);
    }

    @Transactional
    public CustomerView update(long id, CustomerRequest request) {
        get(id);
        rejectDuplicatePhone(request.phone(), id);
        try {
            jdbc.update("""
                    UPDATE customers SET name = ?, phone = ?, wechat = ?, tags = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, clean(request.name()), nullable(request.phone()), nullable(request.wechat()),
                    nullable(request.tags()), nullable(request.notes()), id);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "该手机号已存在，请先核对客户资料");
        }
        operationLogs.record("CUSTOMER_UPDATED", "CUSTOMER", id, "更新客户资料");
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        CustomerView customer = get(id);
        jdbc.update("DELETE FROM customers WHERE id = ?", id);
        operationLogs.record("CUSTOMER_DELETED", "CUSTOMER", id, "删除客户：" + customer.name());
    }

    @Transactional
    public ImportResult importCsv(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
        List<List<String>> rows = parseCsv(content);
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40011, "导入文件为空");
        }
        if (rows.size() > 5001) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40014, "单次最多导入 5000 位客户，请拆分文件后重试");
        }
        List<String> header = rows.get(0).stream().map(String::trim).toList();
        int nameIndex = header.indexOf("姓名");
        if (nameIndex < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40012, "CSV 必须包含“姓名”列");
        }
        int created = 0;
        int skipped = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String name = cell(row, nameIndex);
            if (!StringUtils.hasText(name)) {
                skipped++;
                continue;
            }
            String phone = cell(row, header.indexOf("手机号"));
            if (StringUtils.hasText(phone) && phoneExists(phone, null)) {
                skipped++;
                continue;
            }
            create(new CustomerRequest(name, phone, cell(row, header.indexOf("微信")),
                    cell(row, header.indexOf("标签")), cell(row, header.indexOf("备注"))));
            created++;
        }
        operationLogs.record("CUSTOMER_CSV_IMPORTED", "CUSTOMER", null,
                "总行数 " + Math.max(0, rows.size() - 1) + "，新增 " + created + "，跳过 " + skipped);
        return new ImportResult(Math.max(0, rows.size() - 1), created, skipped);
    }

    public byte[] exportCsv() {
        StringBuilder csv = new StringBuilder("姓名,手机号,微信,标签,备注\r\n");
        for (CustomerView customer : list("")) {
            csv.append(csv(customer.name())).append(',')
                    .append(csv(customer.phone())).append(',')
                    .append(csv(customer.wechat())).append(',')
                    .append(csv(customer.tags())).append(',')
                    .append(csv(customer.notes())).append("\r\n");
        }
        return ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    }

    private void rejectDuplicatePhone(String phone, Long excludedId) {
        if (StringUtils.hasText(phone) && phoneExists(phone.trim(), excludedId)) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "该手机号已存在，请先核对客户资料");
        }
    }

    private boolean phoneExists(String phone, Long excludedId) {
        Integer count = excludedId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE phone = ?", Integer.class, phone.trim())
                : jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE phone = ? AND id <> ?", Integer.class, phone.trim(), excludedId);
        return count != null && count > 0;
    }

    private CustomerView map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CustomerView(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                rs.getString("wechat"), rs.getString("tags"), rs.getString("notes"),
                timestamp(rs.getTimestamp("created_at")), timestamp(rs.getTimestamp("updated_at")));
    }

    private LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullable(String value) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String cell(List<String> row, int index) {
        return index < 0 || index >= row.size() ? "" : row.get(index).trim();
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.matches("^[=+@-].*")) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                if (row.stream().anyMatch(StringUtils::hasText)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40013, "CSV 引号未闭合");
        }
        row.add(field.toString());
        if (row.stream().anyMatch(StringUtils::hasText)) {
            rows.add(row);
        }
        return rows;
    }
}
