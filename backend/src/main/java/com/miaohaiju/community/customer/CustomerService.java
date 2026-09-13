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
import jakarta.validation.Validator;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.miaohaiju.community.customer.CustomerDtos.CustomerRequest;
import static com.miaohaiju.community.customer.CustomerDtos.CustomerView;
import static com.miaohaiju.community.customer.CustomerDtos.ImportResult;
import static com.miaohaiju.community.customer.CustomerDtos.RelationshipProfile;

@Service
public class CustomerService {
    private final JdbcTemplate jdbc;
    private final OperationLogService operationLogs;
    private final Validator validator;

    public CustomerService(JdbcTemplate jdbc, OperationLogService operationLogs, Validator validator) {
        this.jdbc = jdbc;
        this.operationLogs = operationLogs;
        this.validator = validator;
    }

    public List<CustomerView> list(String keyword) {
        return list(keyword, "", "");
    }

    public List<CustomerView> list(String keyword, String industry, String stage) {
        String normalized = keyword == null ? "" : keyword.trim();
        String like = "%" + normalized + "%";
        return jdbc.query("""
                SELECT * FROM customers
                WHERE (name LIKE ? OR phone LIKE ? OR wechat LIKE ? OR tags LIKE ? OR organization LIKE ? OR email LIKE ? OR needs LIKE ?)
                  AND (? = '' OR industry = ?) AND (? = '' OR relationship_stage = ?)
                ORDER BY id DESC LIMIT 500
                """, this::map, like, like, like, like, like, like, like,
                clean(industry), clean(industry), clean(stage), clean(stage));
    }

    public CustomerView get(long id) {
        return jdbc.query("SELECT * FROM customers WHERE id = ?", this::map, id).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, 40401, "客户不存在"));
    }

    @Transactional
    public CustomerView create(CustomerRequest request) {
        validate(request);
        rejectDuplicatePhone(request.phone(), null);
        RelationshipProfile profile = normalizedProfile(request.relationship());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO customers(name, phone, wechat, tags, notes, entity_type, industry, organization,
                          job_title, email, relationship_type, relationship_stage, needs)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, new String[]{"id"});
                statement.setString(1, clean(request.name()));
                statement.setString(2, nullable(request.phone()));
                statement.setString(3, nullable(request.wechat()));
                statement.setString(4, nullable(request.tags()));
                statement.setString(5, nullable(request.notes()));
                statement.setString(6, profile.entityType());
                statement.setString(7, profile.industry());
                statement.setString(8, profile.organization());
                statement.setString(9, profile.jobTitle());
                statement.setString(10, profile.email());
                statement.setString(11, profile.relationshipType());
                statement.setString(12, profile.stage());
                statement.setString(13, profile.needs());
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
        validate(request);
        CustomerView existing = get(id);
        // Older API clients omit relationship; do not erase newly recorded facts on those updates.
        RelationshipProfile profile = request.relationship() == null ? existing.relationship() : normalizedProfile(request.relationship());
        rejectDuplicatePhone(request.phone(), id);
        try {
            jdbc.update("""
                    UPDATE customers SET name = ?, phone = ?, wechat = ?, tags = ?, notes = ?,
                      entity_type = ?, industry = ?, organization = ?, job_title = ?, email = ?,
                      relationship_type = ?, relationship_stage = ?, needs = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, clean(request.name()), nullable(request.phone()), nullable(request.wechat()),
                    nullable(request.tags()), nullable(request.notes()), profile.entityType(), profile.industry(),
                    profile.organization(), profile.jobTitle(), profile.email(), profile.relationshipType(), profile.stage(), profile.needs(), id);
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
                    cell(row, header.indexOf("标签")), cell(row, header.indexOf("备注")),
                    new RelationshipProfile(cell(row, header.indexOf("对象类型")), cell(row, header.indexOf("行业")),
                            cell(row, header.indexOf("所属机构")), cell(row, header.indexOf("职务")), cell(row, header.indexOf("邮箱")),
                            cell(row, header.indexOf("关系类型")), cell(row, header.indexOf("关系阶段")), cell(row, header.indexOf("关注事项")))));
            created++;
        }
        operationLogs.record("CUSTOMER_CSV_IMPORTED", "CUSTOMER", null,
                "总行数 " + Math.max(0, rows.size() - 1) + "，新增 " + created + "，跳过 " + skipped);
        return new ImportResult(Math.max(0, rows.size() - 1), created, skipped);
    }

    public byte[] exportCsv() {
        StringBuilder csv = new StringBuilder("姓名,手机号,微信,标签,备注,对象类型,行业,所属机构,职务,邮箱,关系类型,关系阶段,关注事项\r\n");
        for (CustomerView customer : list("")) {
            csv.append(csv(customer.name())).append(',')
                    .append(csv(customer.phone())).append(',')
                    .append(csv(customer.wechat())).append(',')
                    .append(csv(customer.tags())).append(',')
                    .append(csv(customer.notes())).append(',')
                    .append(csv(customer.relationship().entityType())).append(',')
                    .append(csv(customer.relationship().industry())).append(',')
                    .append(csv(customer.relationship().organization())).append(',')
                    .append(csv(customer.relationship().jobTitle())).append(',')
                    .append(csv(customer.relationship().email())).append(',')
                    .append(csv(customer.relationship().relationshipType())).append(',')
                    .append(csv(customer.relationship().stage())).append(',')
                    .append(csv(customer.relationship().needs())).append("\r\n");
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
                timestamp(rs.getTimestamp("created_at")), timestamp(rs.getTimestamp("updated_at")),
                new RelationshipProfile(rs.getString("entity_type"), rs.getString("industry"), rs.getString("organization"),
                        rs.getString("job_title"), rs.getString("email"), rs.getString("relationship_type"),
                        rs.getString("relationship_stage"), rs.getString("needs")));
    }

    private void validate(CustomerRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40015, "客户字段格式或长度不符合要求，请检查姓名、邮箱和关系资料；导入失败时整批不保存");
        }
    }

    private RelationshipProfile normalizedProfile(RelationshipProfile profile) {
        if (profile == null) return new RelationshipProfile("未分类", "通用关系维护", null, null, null, "未分类", "未标注", null);
        return new RelationshipProfile(orDefault(profile.entityType(), "未分类"), orDefault(profile.industry(), "通用关系维护"),
                nullable(profile.organization()), nullable(profile.jobTitle()), nullable(profile.email()),
                orDefault(profile.relationshipType(), "未分类"), orDefault(profile.stage(), "未标注"), nullable(profile.needs()));
    }

    private String orDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
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
