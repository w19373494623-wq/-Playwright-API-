package com.example.service;

import com.example.model.AutomationConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 登录服务。
 *
 * 职责：
 *  根据 AutomationConfig 调用登录接口，获取最新 Token。
 *  仅处理登录请求和 Token 提取，不修改 Collection / Smoke / 执行逻辑。
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 执行登录并返回 Token。
     *
     * @param config 自动化配置（authType、loginUrl、username、password、tokenField）
     * @return 提取到的 Token 字符串
     * @throws IllegalArgumentException 配置校验不通过
     * @throws RuntimeException 请求失败或 Token 提取失败
     */
    public String login(AutomationConfig config) {
        validate(config);

        try {
            // 构建请求体
            String requestBody = MAPPER.writeValueAsString(
                    java.util.Map.of("username", config.getUsername(), "password", config.getPassword()));

            // 发送登录请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getLoginUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("登录请求: POST {}", config.getLoginUrl());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("登录接口返回非成功状态码: " + response.statusCode()
                        + ", body: " + truncate(response.body(), 200));
            }

            // 解析响应提取 Token
            String token = extractToken(response.body(), config.getTokenField());
            if (token == null || token.isBlank()) {
                throw new RuntimeException("登录响应中未找到 token 字段 '" + config.getTokenField()
                        + "', response: " + truncate(response.body(), 300));
            }

            log.info("登录成功，token 长度: {}", token.length());
            return token;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("登录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 校验自动化配置。
     */
    private void validate(AutomationConfig config) {
        if (!"LOGIN".equals(config.getAuthType())) {
            throw new IllegalArgumentException("authType 必须为 LOGIN，当前: " + config.getAuthType());
        }
        if (isBlank(config.getLoginUrl())) {
            throw new IllegalArgumentException("loginUrl 不能为空");
        }
        if (isBlank(config.getUsername())) {
            throw new IllegalArgumentException("username 不能为空");
        }
        if (isBlank(config.getPassword())) {
            throw new IllegalArgumentException("password 不能为空");
        }
    }

    /**
     * 根据 tokenField 从响应 JSON 中提取 Token。
     * 支持多级路径，如 "data.token" → response.data.token。
     */
    private String extractToken(String responseBody, String tokenField) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode node = root;

            String[] parts = tokenField.split("\\.");
            for (String part : parts) {
                if (node == null || !node.isObject()) return null;
                node = node.get(part);
            }

            return node != null ? node.asText() : null;
        } catch (Exception e) {
            log.warn("Token 提取失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
