CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NULL,
    wechat VARCHAR(100) NULL,
    tags VARCHAR(500) NULL,
    notes TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_customers_name (name),
    UNIQUE INDEX idx_customers_phone_unique (phone)
);

CREATE TABLE contact_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    contact_at TIMESTAMP NOT NULL,
    channel VARCHAR(30) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    next_contact_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_contact_customer_time (customer_id, contact_at),
    INDEX idx_contact_next (next_contact_at),
    CONSTRAINT fk_contact_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
);

CREATE TABLE consumptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    note VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_consumption_customer_time (customer_id, occurred_at),
    CONSTRAINT fk_consumption_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
);

CREATE TABLE operation_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NULL,
    detail VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_operation_created (created_at)
);

CREATE TABLE ai_call_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_type VARCHAR(80) NOT NULL,
    provider VARCHAR(100) NULL,
    model VARCHAR(200) NULL,
    status VARCHAR(30) NOT NULL,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ai_call_created (created_at)
);
