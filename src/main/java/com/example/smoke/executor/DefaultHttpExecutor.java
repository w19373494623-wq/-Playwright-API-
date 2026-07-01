package com.example.smoke.executor;

import com.example.smoke.model.PostmanItem;
import com.example.smoke.model.PostmanRequest;
import com.example.smoke.model.PostmanUrl;
import com.example.smoke.model.SmokeTestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认 HTTP 请求执行器。
 * 使用 java.net.http.HttpClient 实现，支持 JSON / FormData / URLEncoded 请求体。
 */
public class DefaultHttpExecutor implements HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpExecutor.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DefaultHttpExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public DefaultHttpExecutor(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SmokeTestResult execute(PostmanItem item, List<Map<String, String>> variables) {
        SmokeTestResult result = new SmokeTestResult();
        result.setName(item.getName());

        try {
            PostmanRequest request = item.getRequest();
            if (request == null) {
                result.setPassed(false);
                result.setFailureReason("请求对象为空");
                return result;
            }

            String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "GET";
            result.setMethod(method);

            // 追踪本次请求实际使用了哪些变量
            Map<String, String> usedVars = new LinkedHashMap<>();

            // 解析 URL
            String urlStr = resolveUrl(request.getUrl(), variables);
            result.setUrl(urlStr);

            log.info("  → {} {} ({})", method, item.getName(), urlStr);

            // 扫描 URL 中使用的变量
            scanRawUrlVars(request.getUrl(), variables, usedVars);

            // 构建请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(60));

            // 设置请求头
            resolveHeaders(request.getHeader(), variables, requestBuilder);
            // 扫描请求头中使用的变量
            scanHeaderVars(request.getHeader(), variables, usedVars);

            // 设置请求体
            resolveBody(request, variables, requestBuilder, method);
            // 扫描请求体中使用的变量
            scanBodyVars(request, variables, usedVars);

            HttpRequest httpRequest = requestBuilder.build();

            // 发送请求
            long start = System.currentTimeMillis();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            result.setHttpStatus(httpResponse.statusCode());
            result.setDurationMs(duration);
            result.setResponseBody(httpResponse.body());

            // 解析响应头
            Map<String, Object> respHeaders = new LinkedHashMap<>();
            httpResponse.headers().map().forEach((k, v) -> respHeaders.put(k, String.join(", ", v)));
            result.setResponseHeaders(respHeaders);

            log.info("    ← {} {} ({}ms)", httpResponse.statusCode(), method, duration);
            result.setPassed(true);

            // 记录本次请求实际使用的变量
            result.setUsedVariables(usedVars);

            // 从响应中提取变量（token、userId 等）
            Map<String, String> extracted = extractResponseVars(httpResponse.body());
            if (!extracted.isEmpty()) {
                result.setExtractedVariables(extracted);
                log.info("    ← 提取变量: {}", extracted.keySet());
            }

        } catch (java.net.http.HttpConnectTimeoutException e) {
            result.setPassed(false);
            result.setFailureReason("连接超时");
            result.setErrorMessage(e.getMessage());
            log.warn("    ✗ {} 连接超时: {}", item.getName(), e.getMessage());
        } catch (java.net.http.HttpTimeoutException e) {
            result.setPassed(false);
            result.setFailureReason("请求超时");
            result.setErrorMessage(e.getMessage());
            log.warn("    ✗ {} 请求超时: {}", item.getName(), e.getMessage());
        } catch (java.net.ConnectException e) {
            result.setPassed(false);
            result.setFailureReason("连接被拒绝");
            result.setErrorMessage(e.getMessage());
            log.warn("    ✗ {} 连接失败: {}", item.getName(), e.getMessage());
        } catch (Exception e) {
            result.setPassed(false);
            result.setFailureReason("请求异常");
            result.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            log.warn("    ✗ {} 异常: {}", item.getName(), e.getMessage());
        }

        return result;
    }

    // ========== URL 解析 ==========

    /**
     * 从 Postman URL（字符串或对象）解析出完整 URL，并替换变量。
     */
    private String resolveUrl(Object urlObj, List<Map<String, String>> variables) {
        if (urlObj == null) return "";

        String raw;
        if (urlObj instanceof String) {
            raw = (String) urlObj;
        } else if (urlObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> urlMap = (Map<String, Object>) urlObj;
            PostmanUrl postmanUrl = new PostmanUrl();
            postmanUrl.setRaw((String) urlMap.get("raw"));

            Object protocolObj = urlMap.get("protocol");
            if (protocolObj != null) postmanUrl.setProtocol(protocolObj.toString());

            @SuppressWarnings("unchecked")
            List<String> host = (List<String>) urlMap.get("host");
            postmanUrl.setHost(host);

            @SuppressWarnings("unchecked")
            List<String> path = (List<String>) urlMap.get("path");
            postmanUrl.setPath(path);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> query = (List<Map<String, String>>) urlMap.get("query");
            postmanUrl.setQuery(query);

            Object portObj = urlMap.get("port");
            if (portObj != null) postmanUrl.setPort(portObj.toString());

            raw = postmanUrl.buildUrl();
        } else {
            raw = urlObj.toString();
        }

        return replaceVariables(raw, variables);
    }

    // ========== 请求头 ==========

    private void resolveHeaders(List<Map<String, String>> headers,
                                 List<Map<String, String>> variables,
                                 HttpRequest.Builder builder) {
        if (headers == null) return;
        for (Map<String, String> h : headers) {
            String key = h.get("key");
            String value = h.get("value");
            if (key == null || key.isBlank() || value == null) continue;
            if ("host".equalsIgnoreCase(key)) continue;
            String resolvedValue = replaceVariables(value, variables);
            builder.header(key, resolvedValue);
        }
    }

    // ========== 请求体 ==========

    @SuppressWarnings("unchecked")
    private void resolveBody(PostmanRequest request,
                              List<Map<String, String>> variables,
                              HttpRequest.Builder builder,
                              String method) {
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return;
        }
        if (request.getBody() == null) return;

        String mode = (String) request.getBody().get("mode");
        if (mode == null) return;

        switch (mode) {
            case "raw" -> {
                String rawBody = (String) request.getBody().get("raw");
                if (rawBody != null && !rawBody.isBlank()) {
                    String resolved = replaceVariables(rawBody, variables);
                    String contentType = request.getBody().containsKey("options")
                            ? "application/json" : "text/plain";
                    builder.header("Content-Type", contentType);
                    builder.method(method, HttpRequest.BodyPublishers.ofString(resolved, StandardCharsets.UTF_8));
                }
            }
            case "urlencoded" -> {
                List<Map<String, String>> params = (List<Map<String, String>>) request.getBody().get("urlencoded");
                if (params != null && !params.isEmpty()) {
                    String formBody = buildFormBody(params, variables);
                    builder.header("Content-Type", "application/x-www-form-urlencoded");
                    builder.method(method, HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));
                }
            }
            case "formdata" -> {
                List<Map<String, String>> formData = (List<Map<String, String>>) request.getBody().get("formdata");
                if (formData != null && !formData.isEmpty()) {
                    String multipartBody = buildFormData(formData, variables);
                    builder.header("Content-Type", "application/x-www-form-urlencoded");
                    builder.method(method, HttpRequest.BodyPublishers.ofString(multipartBody, StandardCharsets.UTF_8));
                }
            }
            case "file" -> log.warn("file 类型 body 暂不支持");
        }
    }

    private String buildFormBody(List<Map<String, String>> params, List<Map<String, String>> variables) {
        List<String> pairs = new ArrayList<>();
        for (Map<String, String> p : params) {
            String key = p.get("key");
            String value = replaceVariables(p.getOrDefault("value", ""), variables);
            if (key != null) {
                pairs.add(encode(key) + "=" + encode(value));
            }
        }
        return String.join("&", pairs);
    }

    private String buildFormData(List<Map<String, String>> formData, List<Map<String, String>> variables) {
        // 当前简化处理为 x-www-form-urlencoded
        return buildFormBody(formData, variables);
    }

    // ========== 变量替换 ==========

    /** 扫描 URL 中使用了哪些变量 */
    private void scanRawUrlVars(Object urlObj, List<Map<String, String>> variables, Map<String, String> tracker) {
        String raw;
        if (urlObj instanceof String) {
            raw = (String) urlObj;
        } else if (urlObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> urlMap = (Map<String, Object>) urlObj;
            raw = (String) urlMap.get("raw");
        } else {
            return;
        }
        scanVarPatterns(raw, variables, tracker);
    }

    /** 扫描请求头中使用了哪些变量 */
    private void scanHeaderVars(List<Map<String, String>> headers, List<Map<String, String>> variables, Map<String, String> tracker) {
        if (headers == null) return;
        for (Map<String, String> h : headers) {
            String value = h.get("value");
            if (value != null) scanVarPatterns(value, variables, tracker);
        }
    }

    /** 扫描请求体中使用了哪些变量 */
    private void scanBodyVars(PostmanRequest request, List<Map<String, String>> variables, Map<String, String> tracker) {
        if (request.getBody() == null) return;
        String mode = (String) request.getBody().get("mode");
        if (mode == null) return;
        switch (mode) {
            case "raw" -> {
                String rawBody = (String) request.getBody().get("raw");
                if (rawBody != null) scanVarPatterns(rawBody, variables, tracker);
            }
            case "urlencoded", "formdata" -> {
                String listKey = "urlencoded".equals(mode) ? "urlencoded" : "formdata";
                @SuppressWarnings("unchecked")
                List<Map<String, String>> params = (List<Map<String, String>>) request.getBody().get(listKey);
                if (params != null) {
                    for (Map<String, String> p : params) {
                        String value = p.get("value");
                        if (value != null) scanVarPatterns(value, variables, tracker);
                    }
                }
            }
        }
    }

    /** 在字符串中查找 {{varName}} 模式，匹配到变量则记录到 tracker */
    private void scanVarPatterns(String input, List<Map<String, String>> variables, Map<String, String> tracker) {
        if (input == null) return;
        Matcher matcher = VAR_PATTERN.matcher(input);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!tracker.containsKey(varName)) {
                String value = resolveVariable(varName, variables);
                if (value != null) {
                    tracker.put(varName, value);
                } else {
                    tracker.put(varName, "{{" + varName + "}}(未解析)");
                }
            }
        }
    }

    /**
     * 从 HTTP 响应体（JSON）中提取常见变量。
     * 支持嵌套路径如 data.token、data.userId。
     */
    @SuppressWarnings("unchecked")
    Map<String, String> extractResponseVars(String responseBody) {
        Map<String, String> extracted = new LinkedHashMap<>();
        if (responseBody == null || responseBody.isBlank()) return extracted;

        Object parsed;
        try {
            parsed = objectMapper.readValue(responseBody, Object.class);
        } catch (Exception e) {
            return extracted;
        }
        if (!(parsed instanceof Map)) return extracted;

        Map<String, Object> root = (Map<String, Object>) parsed;

        // 提取 token / accessToken / refreshToken
        tryExtract(root, extracted, "token", "token");
        tryExtract(root, extracted, "accessToken", "accessToken");
        tryExtract(root, extracted, "refreshToken", "refreshToken");
        tryNestedExtract(root, extracted, "data.token", "token");
        tryNestedExtract(root, extracted, "data.accessToken", "accessToken");
        tryNestedExtract(root, extracted, "data.refreshToken", "refreshToken");

        // 提取 userId / uid
        tryExtract(root, extracted, "userId", "userId");
        tryExtract(root, extracted, "uid", "userId");
        tryNestedExtract(root, extracted, "data.userId", "userId");
        tryNestedExtract(root, extracted, "data.uid", "userId");
        tryNestedExtract(root, extracted, "data.user.id", "userId");
        tryNestedExtract(root, extracted, "data.user.userId", "userId");

        // 提取 orderId / sessionId
        tryExtract(root, extracted, "orderId", "orderId");
        tryExtract(root, extracted, "sessionId", "sessionId");
        tryNestedExtract(root, extracted, "data.orderId", "orderId");

        // 提取 username / nickname
        tryExtract(root, extracted, "username", "username");
        tryExtract(root, extracted, "nickname", "nickname");
        tryNestedExtract(root, extracted, "data.username", "username");
        tryNestedExtract(root, extracted, "data.nickname", "nickname");

        return extracted;
    }

    private void tryExtract(Map<String, Object> root, Map<String, String> target, String field, String varName) {
        Object val = root.get(field);
        if (val instanceof String s && !s.isBlank()) {
            target.putIfAbsent(varName, s);
        }
    }

    private void tryNestedExtract(Map<String, Object> root, Map<String, String> target, String jsonPath, String varName) {
        String[] parts = jsonPath.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return;
            }
        }
        if (current instanceof String s && !s.isBlank()) {
            target.putIfAbsent(varName, s);
        }
    }

    /**
     * 将字符串中的 {{varName}} 替换为环境变量值。
     */
    String replaceVariables(String input, List<Map<String, String>> variables) {
        if (input == null || variables == null || variables.isEmpty()) return input;

        Matcher matcher = VAR_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = resolveVariable(varName, variables);
            if (replacement == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("{{" + varName + "}}"));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolveVariable(String name, List<Map<String, String>> variables) {
        if (variables == null) return null;
        for (Map<String, String> v : variables) {
            if (name.equals(v.get("key"))) {
                return v.get("value");
            }
        }
        // 尝试系统环境变量
        String envVal = System.getenv(name);
        if (envVal != null) return envVal;
        // 尝试系统属性
        return System.getProperty(name);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
