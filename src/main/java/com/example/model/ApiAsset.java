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

    // 录制元信息
    private String pageUrl;
    private String sessionId;
    private int sequence;

    // AI 分析结果
    private String businessStep;
    private String intent;
    private Float confidence;

    public ApiAsset() {}

    public ApiAsset(String url, String method, String requestBody, int statusCode, String responseBody) {
        this.url = url;
        this.method = method;
        this.requestBody = requestBody;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.request.put("body", requestBody);
        this.response.put("body", responseBody);
    }

    /** 从 CapturedRequest 构造（带解析、指纹、标签） */
    public ApiAsset(CapturedRequest req, int seq, String pageUrl, String sessionId) {
        this.url = req.getUrl();
        this.method = req.getMethod();
        this.requestBody = req.getRequestBody();
        this.statusCode = req.getStatusCode();
        this.responseBody = req.getResponseBody();
        this.pageUrl = pageUrl;
        this.sessionId = sessionId;
        this.sequence = seq;

        // 请求/响应详情
        this.request.put("headers", req.getRequestHeaders());
        this.request.put("body", req.getRequestBody());
        this.response.put("headers", req.getResponseHeaders());
        this.response.put("body", req.getResponseBody());

        // 解析 domain + resource
        parseUrl();

        // 规则标签
        this.ruleTags = tagRequest();

        // 指纹 = method:domain:resource (不含 query，用于合并同类请求)
        this.fingerprint = method + ":" + (domain != null ? domain : "") + ":" + (resource != null ? resource : "");
    }

    /** 按指纹合并多个相同接口的请求（保留第一个，记录合并来源） */
    public static ApiAsset merge(ApiAsset primary, List<ApiAsset> others) {
        List<String> mergedFrom = new ArrayList<>();
        mergedFrom.add(primary.getUrl());
        for (ApiAsset other : others) {
            mergedFrom.add(other.getUrl());
            // 如果有响应体且 primary 没有，补全
            if (primary.getResponseBody() == null && other.getResponseBody() != null) {
                primary.setResponseBody(other.getResponseBody());
                primary.getResponse().put("body", other.getResponseBody());
            }
        }
        primary.setMergedFrom(mergedFrom);
        return primary;
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
}
