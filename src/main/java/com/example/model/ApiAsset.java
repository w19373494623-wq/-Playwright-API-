package com.example.model;

import java.net.URI;
import java.util.*;

public class ApiAsset {

    // 原始请求数据
    private String url;
    private String method;
    private String requestBody;
    private int statusCode;
    private String responseBody;

    // 解析后的结构化字段
    private String domain;
    private String resource;
    private List<String> ruleTags;
    private String fingerprint;
    private List<String> mergedFrom;

    // 请求/响应详情（Map 结构方便序列化）
    private Map<String, Object> request = new LinkedHashMap<>();
    private Map<String, Object> response = new LinkedHashMap<>();

    // 结构化字段
    private Map<String, String> headers;
    private Map<String, Object> query;
    private Map<String, Object> requestSchema;
    private Map<String, Object> responseSchema;

    // 录制元信息
    private String pageUrl;
    private String sessionId;
    private int sequence;

    // AI 分析结果
    private String businessStep;
    private String intent;
    private Float confidence;

    // 接口分类: business | auth | static | monitoring | third_party
    private String category;

    public ApiAsset() {}

    public ApiAsset(String url, String method, String requestBody, int statusCode, String responseBody) {
        this.url = url;
        this.method = method;
        this.requestBody = requestBody;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.request.put("body", requestBody);
        this.response.put("body", responseBody);

        // 自动解析
        parseUrl();
        this.ruleTags = tagRequest();
        this.fingerprint = method + ":" + (domain != null ? domain : "") + ":" + (resource != null ? resource : "");
    }

    private void parseUrl() {
        if (url == null) { domain = "unknown"; resource = "/"; return; }
        try {
            URI uri = URI.create(url);
            domain = uri.getHost();
            String path = uri.getPath();
            resource = (path != null && !path.isEmpty()) ? path : "/";
        } catch (Exception e) {
            domain = "unknown";
            resource = url;
        }
    }

    private List<String> tagRequest() {
        List<String> tags = new ArrayList<>();
        if (url == null) return tags;
        String lower = url.toLowerCase();
        if (lower.contains("login") || lower.contains("signin") || lower.contains("auth")) tags.add("auth");
        if (lower.contains("logout") || lower.contains("signout")) tags.add("logout");
        if (lower.contains("api")) tags.add("api");
        if (lower.contains("graphql")) tags.add("graphql");
        if (lower.contains("swagger") || lower.contains("openapi")) tags.add("doc");
        if (lower.contains("health") || lower.contains("ping")) tags.add("health");
        if (lower.contains("upload")) tags.add("upload");
        if (lower.contains("download") || lower.contains("export")) tags.add("download");
        if (lower.contains("error") || lower.contains("exception")) tags.add("error");
        if (method != null && method.equalsIgnoreCase("post")) tags.add("write");
        if (method != null && method.equalsIgnoreCase("get")) tags.add("read");
        if (method != null && (method.equalsIgnoreCase("put") || method.equalsIgnoreCase("patch"))) tags.add("update");
        if (method != null && method.equalsIgnoreCase("delete")) tags.add("delete");
        return tags;
    }

    // ===== getters & setters =====

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public List<String> getRuleTags() { return ruleTags; }
    public void setRuleTags(List<String> ruleTags) { this.ruleTags = ruleTags; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public List<String> getMergedFrom() { return mergedFrom; }
    public void setMergedFrom(List<String> mergedFrom) { this.mergedFrom = mergedFrom; }

    public Map<String, Object> getRequest() { return request; }
    public void setRequest(Map<String, Object> request) { this.request = request; }

    public Map<String, Object> getResponse() { return response; }
    public void setResponse(Map<String, Object> response) { this.response = response; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public Map<String, Object> getQuery() { return query; }
    public void setQuery(Map<String, Object> query) { this.query = query; }

    public Map<String, Object> getRequestSchema() { return requestSchema; }
    public void setRequestSchema(Map<String, Object> requestSchema) { this.requestSchema = requestSchema; }

    public Map<String, Object> getResponseSchema() { return responseSchema; }
    public void setResponseSchema(Map<String, Object> responseSchema) { this.responseSchema = responseSchema; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public String getBusinessStep() { return businessStep; }
    public void setBusinessStep(String businessStep) { this.businessStep = businessStep; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public Float getConfidence() { return confidence; }
    public void setConfidence(Float confidence) { this.confidence = confidence; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
