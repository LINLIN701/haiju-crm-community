ALTER TABLE customers ADD COLUMN entity_type VARCHAR(30) NOT NULL DEFAULT '未分类';
ALTER TABLE customers ADD COLUMN industry VARCHAR(60) NOT NULL DEFAULT '通用关系维护';
ALTER TABLE customers ADD COLUMN organization VARCHAR(200) NULL;
ALTER TABLE customers ADD COLUMN job_title VARCHAR(100) NULL;
ALTER TABLE customers ADD COLUMN email VARCHAR(200) NULL;
ALTER TABLE customers ADD COLUMN relationship_type VARCHAR(60) NOT NULL DEFAULT '未分类';
ALTER TABLE customers ADD COLUMN relationship_stage VARCHAR(60) NOT NULL DEFAULT '未标注';
ALTER TABLE customers ADD COLUMN needs VARCHAR(2000) NULL;
CREATE INDEX idx_customers_industry_stage ON customers(industry, relationship_stage);
