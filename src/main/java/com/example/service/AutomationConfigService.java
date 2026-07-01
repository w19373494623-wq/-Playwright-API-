package com.example.service;

import com.example.model.AutomationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 自动化配置服务。
 *
 * 职责：
 *  - 读取/保存自动化配置（认证方式、登录接口等）
 *  - 配置持久化到 storage/automation/config.json
 */
@Service
public class AutomationConfigService {

    private static final Logger log = LoggerFactory.getLogger(AutomationConfigService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path configDir = Paths.get("storage", "automation");
    private final Path configPath = configDir.resolve("config.json");

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(configPath)) {
                MAPPER.writeValue(configPath.toFile(), new AutomationConfig());
                log.info("初始化自动化配置文件: {}", configPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("初始化自动化配置目录失败", e);
        }
    }

    /**
     * 读取当前自动化配置。
     */
    public AutomationConfig getConfig() {
        try {
            if (Files.exists(configPath)) {
                return MAPPER.readValue(configPath.toFile(), AutomationConfig.class);
            }
        } catch (IOException e) {
            log.warn("读取自动化配置失败，返回默认配置", e);
        }
        return new AutomationConfig();
    }

    /**
     * 保存自动化配置。
     */
    public void saveConfig(AutomationConfig config) {
        try {
            Files.createDirectories(configDir);
            MAPPER.writeValue(configPath.toFile(), config);
            log.info("自动化配置已保存");
        } catch (IOException e) {
            throw new RuntimeException("保存自动化配置失败", e);
        }
    }
}
