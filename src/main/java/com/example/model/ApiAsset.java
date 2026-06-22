package com.example.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApiAsset {

    // ========== 原始层（从浏览器直接捕获）==========
    private String sessionId;
    private String pageUrl;
    private long timestamp;
    private int sequenceNumber;
    private String method;
    private String url;
    private Map<String, Object> request;
    private Map<String, Object> response;
    private int statusCode;

    // ========== 规则层（确定性，代码计算）==========
    private String domain;          // auth/user/order/product...
    private String resource;        // login/info/submit/list...
    private Set<String> ruleTags;   // auth/query/mutation/payment/upload
    private String fingerprint;     // method:url:hash(requestBody)
    private boolean isDuplicate;    // 是否被指纹合并
    private List<String> mergedFrom; // 被合并掉的原始 URL 列表

    // ========== AI 层（不可靠但语义强，由 AI 填充）==========
    private String businessStep;    // 登录/浏览商品/下单/支付
    private String intent;          // 查询/提交/更新/删除
    private Float confidence;       // AI 判断置信度 0.0~1.0

    public ApiAsset() {
    }

    /**
     * 从原始捕获数据构建 ApiAsset，规则层自动计算
     */
    public ApiAsset(CapturedRequest raw, int seq, String pageUrl, String sessionId) {
        // --- 原始层 ---
        this.sessionId = sessionId;
        this.pageUrl = pageUrl;
        this.timestamp = raw.getTimestamp();
        this.sequenceNumber = seq;
        this.method = raw.getMethod();
        this.url = raw.getUrl();
        this.statusCode = raw.getStatusCode();

        Map<String, Object> req = new LinkedHashMap<>();
        if (raw.getRequestHeaders() != null && !raw.getRequestHeaders().isEmpty()) {
            req.put("headers", new LinkedHashMap<>(raw.getRequestHeaders()));
        }
        if (raw.getRequestBody() != null && !raw.getRequestBody().isEmpty()) {
            req.put("body", raw.getRequestBody());
        }
        this.request = req;

        Map<String, Object> res = new LinkedHashMap<>();
        if (raw.getResponseHeaders() != null && !raw.getResponseHeaders().isEmpty()) {
            res.put("headers", new LinkedHashMap<>(raw.getResponseHeaders()));
        }
        if (raw.getResponseBody() != null && !raw.getResponseBody().isEmpty()) {
            res.put("body", raw.getResponseBody());
        }
        this.response = res;

        // --- 规则层 ---
        String path = extractPath(url);
        this.domain = inferDomain(path);
        this.resource = inferResource(path);
        this.ruleTags = inferRuleTags(raw);
        this.fingerprint = computeFingerprint();
        this.isDuplicate = false;
        this.mergedFrom = new ArrayList<>();

        // --- AI 层（初始为空，由 AI 填充）---
        this.businessStep = null;
        this.intent = null;
        this.confidence = null;
    }

    /**
     * 当同指纹被合并时，创建合并后的资产
     */
    public static ApiAsset merge(ApiAsset primary, List<ApiAsset> others) {
        if (others.isEmpty()) return primary;

        // 保留最新响应
        ApiAsset latest = primary;
        List<String> mergedUrls = new ArrayList<>();
        for (ApiAsset other : others) {
            mergedUrls.add(other.method + " " + other.url);
            if (other.timestamp > latest.timestamp) {
                latest = other;
            }
        }

        ApiAsset merged = new ApiAsset();
        // 原始层 - 使用主记录的元数据，但响应用最新的
        merged.sessionId = primary.sessionId;
        merged.pageUrl = primary.pageUrl;
        merged.timestamp = primary.timestamp;
        merged.sequenceNumber = primary.sequenceNumber;
        merged.method = primary.method;
        merged.url = primary.url;
        merged.request = primary.request;
        merged.response = latest.response;
        merged.statusCode = latest.statusCode;

        // 规则层
        merged.domain = primary.domain;
        merged.resource = primary.resource;
        merged.ruleTags = new LinkedHashSet<>(primary.ruleTags);
        merged.fingerprint = primary.fingerprint;
        merged.isDuplicate = false;
        merged.mergedFrom = mergedUrls;

        // AI 层 - 保留 primary 的
        merged.businessStep = primary.businessStep;
        merged.intent = primary.intent;
        merged.confidence = latest.confidence;

        return merged;
    }

    public String computeFingerprint() {
        String body = (String) request.get("body");
        String bodyHash = "";
        if (body != null && !body.isEmpty()) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(body.getBytes(StandardCharsets.UTF_8));
                bodyHash = HexFormat.of().formatHex(digest).substring(0, 8);
            } catch (NoSuchAlgorithmException e) {
                bodyHash = Integer.toHexString(body.hashCode());
            }
        }
        return method + ":" + normalizeUrlForFingerprint(url) + ":" + bodyHash;
    }

    private String normalizeUrlForFingerprint(String url) {
        String path = extractPath(url);
        // 动态参数统一化：/user/123 → /user/:id
        return path.replaceAll("/\\d+", "/:id")
                   .replaceAll("/[a-f0-9]{24,}", "/:oid")
                   .replaceAll("/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", "/:uuid")
                   .replaceAll("\\?(.*)$", "");  // 去掉 query string
    }

    // ========== 规则层计算 ==========

    private String extractPath(String url) {
        try {
            return java.net.URI.create(url).getPath();
        } catch (Exception e) {
            return url;
        }
    }

    private String inferDomain(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) return "root";
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            // 常见 api 前缀跳过
            if (part.equals("api") || part.equals("v1") || part.equals("v2") || part.matches("v\\d+")) continue;
            return part;
        }
        return "other";
    }

    private String inferResource(String path) {
        if (path == null || path.isEmpty()) return "index";
        String[] parts = path.split("/");
        // 取最后一个有意义的段
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i];
            if (p.isEmpty() || p.matches("\\d+") || p.equals("api") || p.matches("v\\d+")) continue;
            // 去掉 query string
            int qm = p.indexOf('?');
            return qm > 0 ? p.substring(0, qm) : p;
        }
        return "index";
    }

    private Set<String> inferRuleTags(CapturedRequest raw) {
        Set<String> tags = new LinkedHashSet<>();
        String lowerUrl = raw.getUrl().toLowerCase();
        String method = raw.getMethod().toUpperCase();

        // HTTP 方法语义
        if ("GET".equals(method)) tags.add("query");
        else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            tags.add("mutation");
        } else if ("DELETE".equals(method)) tags.add("mutation");

        // URL 模式语义
        if (lowerUrl.contains("login") || lowerUrl.contains("auth") || lowerUrl.contains("token")
                || lowerUrl.contains("signin") || lowerUrl.contains("signup") || lowerUrl.contains("register")) {
            tags.add("auth");
        }
        if (lowerUrl.contains("pay") || lowerUrl.contains("order") || lowerUrl.contains("checkout")
                || lowerUrl.contains("charge") || lowerUrl.contains("transaction")) {
            tags.add("payment");
        }
        if (lowerUrl.contains("search") || lowerUrl.contains("query") || lowerUrl.contains("list")
                || lowerUrl.contains("find") || lowerUrl.contains("filter")) {
            tags.add("query");
        }
        if (lowerUrl.contains("user") || lowerUrl.contains("profile") || lowerUrl.contains("member")
                || lowerUrl.contains("account")) {
            tags.add("user");
        }
        if (lowerUrl.contains("upload") || lowerUrl.contains("file") || lowerUrl.contains("image")) {
            tags.add("upload");
        }
        if (lowerUrl.contains("config") || lowerUrl.contains("setting") || lowerUrl.contains("option")) {
            tags.add("config");
        }

        return tags;
    }

    // ========== getters/setters ==========

    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { sessionId = v; }
    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String v) { pageUrl = v; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long v) { timestamp = v; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int v) { sequenceNumber = v; }
    public String getMethod() { return method; }
    public void setMethod(String v) { method = v; }
    public String getUrl() { return url; }
    public void setUrl(String v) { url = v; }
    public Map<String, Object> getRequest() { return request; }
    public void setRequest(Map<String, Object> v) { request = v; }
    public Map<String, Object> getResponse() { return response; }
    public void setResponse(Map<String, Object> v) { response = v; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int v) { statusCode = v; }

    public String getDomain() { return domain; }
    public void setDomain(String v) { domain = v; }
    public String getResource() { return resource; }
    public void setResource(String v) { resource = v; }
    public Set<String> getRuleTags() { return ruleTags; }
    public void setRuleTags(Set<String> v) { ruleTags = v; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String v) { fingerprint = v; }
    public boolean isDuplicate() { return isDuplicate; }
    public void setDuplicate(boolean v) { isDuplicate = v; }
    public List<String> getMergedFrom() { return mergedFrom; }
    public void setMergedFrom(List<String> v) { mergedFrom = v; }

    public String getBusinessStep() { return businessStep; }
    public void setBusinessStep(String v) { businessStep = v; }
    public String getIntent() { return intent; }
    public void setIntent(String v) { intent = v; }
    public Float getConfidence() { return confidence; }
    public void setConfidence(Float v) { confidence = v; }
}
