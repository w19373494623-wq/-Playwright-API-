package com.example.capture;

import com.example.analysis.SchemaInferrer;
import com.example.analysis.UrlParser;
import com.example.model.ApiAsset;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class ResponseInterceptor implements NetworkInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ResponseInterceptor.class);

    private final SchemaInferrer schemaInferrer;
    private final UrlParser urlParser;

    public ResponseInterceptor(SchemaInferrer schemaInferrer, UrlParser urlParser) {
        this.schemaInferrer = schemaInferrer;
        this.urlParser = urlParser;
    }

    @Override
    public void install(Page page, Consumer<ApiAsset> sink) {
        page.onResponse(response -> handleResponse(response, sink));
    }

    @Override
    public void uninstall(Page page) {
        // listeners die with the page
    }

    // 只保留 XHR 和 Fetch 资源类型，其余（document/script/image/font/stylesheet等）全部丢弃
    private static final java.util.Set<String> KEPT_RESOURCE_TYPES = java.util.Set.of("xhr", "fetch");

    // 按 URL 路径保留的业务接口模式
    private static final java.util.Set<String> API_PATH_PATTERNS = java.util.Set.of(
            "/api/", "/v1/", "/v2/", "/graphql"
    );

    private void handleResponse(Response response, Consumer<ApiAsset> sink) {
        try {
            String reqUrl = response.request().url();
            String method = response.request().method();

            if (reqUrl.startsWith("data:") || reqUrl.startsWith("blob:")) return;
            if ("OPTIONS".equalsIgnoreCase(method)) return;

            // === 核心：按 resourceType 过滤，只保留 xhr/fetch ===
            String resourceType = response.request().resourceType();
            if (resourceType == null || !KEPT_RESOURCE_TYPES.contains(resourceType)) {
                return;
            }

            // === 二次过滤：只保留 JSON 接口或含 API 路径的请求 ===
            String lowerUrl = reqUrl.toLowerCase();
            boolean hasApiPath = false;
            for (String p : API_PATH_PATTERNS) {
                if (lowerUrl.contains(p)) { hasApiPath = true; break; }
            }
            if (!hasApiPath) {
                // 没有 API 路径特征 → 初步检查 content-type 是否含 json
                String ct = response.headers().getOrDefault("content-type", "");
                if (!ct.toLowerCase().contains("json")) {
                    return; // 非 JSON 且无 API 路径，丢弃
                }
            }

            int statusCode;
            try {
                statusCode = response.status();
            } catch (Exception e) {
                log.warn("获取状态码失败: {} {}", method, reqUrl);
                return;
            }

            String requestBody = null;
            try {
                requestBody = response.request().postData();
            } catch (Exception ignored) {}

            Map<String, String> requestHeaders = new LinkedHashMap<>();
            try {
                Map<String, String> rawHeaders = response.request().headers();
                if (rawHeaders != null) requestHeaders.putAll(rawHeaders);
            } catch (Exception ignored) {}

            Map<String, Object> queryParams = urlParser.parseQueryParams(reqUrl);

            String responseBody = null;
            String contentType = requestHeaders.getOrDefault("content-type", "");
            try {
                Map<String, String> respHeaders = response.headers();
                if (respHeaders != null) {
                    String ct = respHeaders.getOrDefault("content-type", "");
                    if (!ct.isEmpty()) contentType = ct;
                }
            } catch (Exception ignored) {}

            if (isTextContent(contentType)) {
                try {
                    byte[] body = response.body();
                    if (body != null && body.length > 0 && body.length < 512 * 1024) {
                        responseBody = new String(body, StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    log.warn("读取响应体失败: {} {} (status={})", method, reqUrl, statusCode);
                }
            }

            ApiAsset asset = new ApiAsset(reqUrl, method, requestBody, statusCode, responseBody);
            asset.setHeaders(requestHeaders);
            asset.setQuery(queryParams);
            asset.setRequestSchema(schemaInferrer.inferSchema(requestBody));
            asset.setResponseSchema(schemaInferrer.inferSchema(responseBody));
            try { asset.getResponse().put("content-type", contentType); } catch (Exception ignored) {}

            log.info("捕获[onResponse]: {} {} -> {} [{}]", method, reqUrl, statusCode, contentType);

            sink.accept(asset);
        } catch (Exception e) {
            log.error("ResponseInterceptor 异常: {}", e.getMessage(), e);
        }
    }

    private boolean isTextContent(String contentType) {
        if (contentType == null || contentType.isEmpty()) return false;
        String ct = contentType.toLowerCase();
        return ct.contains("json") || ct.contains("xml")
                || ct.contains("text") || ct.contains("javascript")
                || ct.contains("x-www-form-urlencoded");
    }
}
