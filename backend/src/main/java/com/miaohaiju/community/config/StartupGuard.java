package com.miaohaiju.community.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StartupGuard implements ApplicationRunner {
    private final String adminPassword;
    private final String datasourceUrl;

    public StartupGuard(
            @Value("${app.admin.password:}") String adminPassword,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.adminPassword = adminPassword;
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminPassword) || adminPassword.length() < 12) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD 必须配置为至少 12 位的独立强密码");
        }
        if (!datasourceUrl.startsWith("jdbc:mysql://") && !datasourceUrl.startsWith("jdbc:h2:mem:")) {
            throw new IllegalStateException("社区版只允许 MySQL，测试环境只允许内存 H2");
        }
    }
}
