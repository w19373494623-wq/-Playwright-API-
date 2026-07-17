package com.example.service;

import com.example.model.ApiAsset;
import com.example.model.ApiSummary;
import com.example.model.BusinessFlow;
import com.example.model.DedupResult;
import com.example.model.HistoryRecord;
import com.example.model.SmokeTestSummary;
import com.example.repository.HistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    private final HistoryRepository historyRepository;
    private final DedupService dedupService;

    public HistoryService(HistoryRepository historyRepository, DedupService dedupService) {
        this.historyRepository = historyRepository;
        this.dedupService = dedupService;
    }

    /**
     * 保存录制项目快照。
     * 包含完整接口资产 + 精简列表，不依赖 AI 分析结果。
     */
    public HistoryRecord save(List<ApiAsset> assets,
                              String mainDomain, String pageUrl, long startTime) {
        DedupResult dedupResult = dedupService.dedup(assets);

        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        HistoryRecord record = new HistoryRecord();
        record.setId(id);
        record.setTitle(buildTitle(mainDomain, now));
        record.setUrl(pageUrl);
        record.setMainDomain(mainDomain);
        record.setCreatedAt(now);
        record.setUpdateTime(now);
        record.setDuration((now - startTime) / 1000);
        record.setVersion(2);

        record.setTotalRaw(dedupResult.getTotalRaw());
        record.setTotalFiltered(assets.size());
        record.setApiCount(dedupResult.getTotal());

        // 保存完整资产（供测试执行、详情展示）
        record.setAssets(new ArrayList<>(assets));

        // 保存精简列表（供前端列表展示）
        record.setApis(buildApiSummaries(assets, dedupResult));

        // 提取并保存环境变量
        record.setEnvVars(extractEnvVars(assets));

        historyRepository.save(record);
        log.info("项目快照已保存: id={}, title={}, apis={}, assets={}, envVars={}",
                id, record.getTitle(), record.getApis().size(), assets.size(), record.getEnvVars().size());
        return record;
    }

    /**
     * 统一更新项目：所有更新操作通过此方法，保证 updateTime 同步。
     */
    public void updateProject(HistoryRecord record) {
        if (record == null) return;
        record.setUpdateTime(System.currentTimeMillis());
        historyRepository.save(record);
    }

    /**
     * 更新 AI 分析结果（业务链路）。
     */
    public void updateAiResult(String id, String summary, BusinessFlow businessFlow) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新AI结果失败，历史记录不存在: id={}", id);
            return;
        }
        if (summary != null) record.setSummary(summary);
        if (businessFlow != null) record.setBusinessFlow(businessFlow);
        updateProject(record);
        log.info("AI业务链路已更新: id={}", id);
    }

    /**
     * 更新 AI 分析扩展数据到 aiAnalysis Map。
     */
    public void updateAiAnalysis(String id, String key, Object value) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新AI分析扩展数据失败，历史记录不存在: id={}", id);
            return;
        }
        Map<String, Object> aiAnalysis = record.getAiAnalysis();
        if (aiAnalysis == null) {
            aiAnalysis = new LinkedHashMap<>();
        }
        aiAnalysis.put(key, value);
        record.setAiAnalysis(aiAnalysis);
        updateProject(record);
        log.info("AI分析扩展数据已更新: id={}, key={}", id, key);
    }

    /**
     * 更新 AI 去重结果缓存。
     */
    public void updateDedupResult(String id, DedupResult dedupResult) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新去重结果失败，历史记录不存在: id={}", id);
            return;
        }
        record.setDedupResult(dedupResult);
        updateProject(record);
        log.info("AI去重结果已缓存: id={}, total={}", id, dedupResult.getTotal());
    }

    /**
     * 更新环境变量。
     */
    public void updateEnvVars(String id, Map<String, String> envVars) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新环境变量失败，历史记录不存在: id={}", id);
            return;
        }
        record.setEnvVars(envVars);
        updateProject(record);
        log.info("环境变量已更新: id={}, vars={}", id, envVars.keySet());
    }

    /**
     * 统一更新 token 到环境变量。
     * 支持 token/accessToken/access_token/jwt，合并到现有 envVars 中。
     */
    public void updateToken(String id, String tokenKey, String tokenValue) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新 token 失败，历史记录不存在: id={}", id);
            return;
        }
        Map<String, String> envVars = record.getEnvVars();
        if (envVars == null) {
            envVars = new java.util.LinkedHashMap<>();
        }
        envVars.put(tokenKey, tokenValue);
        record.setEnvVars(envVars);
        updateProject(record);
        log.info("token 已更新到历史记录: id={}, key={}, length={}", id, tokenKey, tokenValue.length());
    }

    /**
     * 更新烟雾测试结果摘要。
     */
    public void updateSmokeResult(String id, SmokeTestSummary result) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新烟雾测试结果失败，历史记录不存在: id={}", id);
            return;
        }
        record.setSmokeTestResult(result);
        updateProject(record);
        log.info("烟雾测试结果已保存: id={}, {}/{} 通过", id, result.getPassed(), result.getTotal());
    }

    /**
     * 更新多场景识别结果。
     */
    public void updateScenarios(String id, List<Map<String, Object>> scenarios) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新场景结果失败，历史记录不存在: id={}", id);
            return;
        }
        record.setScenarios(scenarios);
        updateProject(record);
        log.info("多场景识别结果已更新: id={}, count={}", id, scenarios != null ? scenarios.size() : 0);
    }

    /**
     * 查询历史列表（仅索引信息）。
     */
    public List<Map<String, Object>> findAll() {
        return historyRepository.findAllIndex();
    }

    /**
     * 查询历史详情（完整记录）。
     */
    public HistoryRecord findById(String id) {
        return historyRepository.findById(id);
    }

    /**
     * 删除历史记录。
     */
    public void deleteById(String id) {
        historyRepository.deleteById(id);
        log.info("历史记录已删除: id={}", id);
    }

    /**
     * 更新历史记录标题。
     */
    public void updateTitle(String id, String newTitle) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新标题失败，历史记录不存在: id={}", id);
            return;
        }
        record.setTitle(newTitle);
        updateProject(record);
        log.info("历史记录标题已更新: id={}, title={}", id, newTitle);
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 HistoryRecord 转为 Postman Collection v2.1 JSON 字符串。
     * 优先使用完整 assets，降级到 apis。
     */
    public String toApifoxCollectionJson(HistoryRecord record) {
        try {
            Map<String, Object> collection = buildApifoxCollection(record);
            return objectMapper.writeValueAsString(collection);
        } catch (Exception e) {
            throw new RuntimeException("转换 Collection JSON 失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildApifoxCollection(HistoryRecord record) {
        String baseUrl = "https://" + (record.getMainDomain() != null ? record.getMainDomain() : "unknown");
        String name = (record.getTitle() != null ? record.getTitle() : "历史记录") + " - 自动化测试";

        Map<String, Object> collection = new LinkedHashMap<>();

        // info
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", name);
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.put("info", info);

        // variable
        List<Map<String, String>> varList = new ArrayList<>();
        varList.add(Map.of("key", "baseUrl", "value", baseUrl));
        if (record.getEnvVars() != null && !record.getEnvVars().isEmpty()) {
            for (Map.Entry<String, String> e : record.getEnvVars().entrySet()) {
                varList.add(Map.of("key", e.getKey(), "value", e.getValue() != null ? e.getValue() : ""));
            }
        } else {
            varList.add(Map.of("key", "token", "value", ""));
            varList.add(Map.of("key", "userId", "value", ""));
        }
        collection.put("variable", varList);

        // 优先用 assets，降级到 apis
        List<?> sourceItems = record.getAssets() != null ? record.getAssets() : record.getApis();
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

        if (sourceItems != null) {
            for (Object item : sourceItems) {
                String resource;
                String method;
                if (item instanceof ApiAsset asset) {
                    method = asset.getMethod() != null ? asset.getMethod() : "GET";
                    resource = asset.getResource() != null ? asset.getResource() : "/";
                } else if (item instanceof ApiSummary api) {
                    method = api.getMethod() != null ? api.getMethod() : "GET";
                    resource = api.getResource() != null ? api.getResource() : "/";
                } else {
                    continue;
                }
                String module = inferModuleFromUrl(resource);
                grouped.computeIfAbsent(module, k -> new ArrayList<>()).add(buildItemMap(item, baseUrl));
            }
        }

        // item (folders)
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> folder = new LinkedHashMap<>();
            folder.put("name", entry.getKey());
            folder.put("item", entry.getValue());
            items.add(folder);
        }
        collection.put("item", items);

        return collection;
    }

    private String inferModuleFromUrl(String url) {
        if (url == null) return "其他";
        String lower = url.toLowerCase();
        if (lower.contains("/user/") || lower.contains("/user?")) return "用户模块";
        if (lower.contains("/login") || lower.contains("/auth/") || lower.contains("/token")) return "登录鉴权";
        if (lower.contains("/order/") || lower.contains("/pay/") || lower.contains("/payment")) return "订单支付";
        if (lower.contains("/content/") || lower.contains("/ugc/") || lower.contains("/post/") || lower.contains("/feed")) return "内容模块";
        if (lower.contains("/comment/") || lower.contains("/reply/")) return "评论模块";
        if (lower.contains("/follow") || lower.contains("/fans") || lower.contains("/relation")) return "关注模块";
        if (lower.contains("/message/") || lower.contains("/im/") || lower.contains("/chat")) return "消息模块";
        if (lower.contains("/search")) return "搜索模块";
        if (lower.contains("/commerce/") || lower.contains("/product/") || lower.contains("/shop")) return "商城模块";
        if (lower.contains("/upload") || lower.contains("/file")) return "文件模块";
        if (lower.contains("/setting") || lower.contains("/config") || lower.contains("/privacy")) return "设置模块";
        return "其他";
    }

    private Map<String, Object> buildItemMap(Object item, String baseUrl) {
        String method;
        String resource;
        String apiName;
        Map<String, String> storedHeaders;
        String requestBody;
        String fullUrl;

        if (item instanceof ApiAsset asset) {
            method = asset.getMethod() != null ? asset.getMethod() : "GET";
            resource = asset.getResource() != null ? asset.getResource() : "/";
            apiName = method + " " + resource;
            storedHeaders = asset.getHeaders();
            requestBody = asset.getRequestBody();
            fullUrl = asset.getUrl();
        } else if (item instanceof ApiSummary api) {
            method = api.getMethod() != null ? api.getMethod() : "GET";
            resource = api.getResource() != null ? api.getResource() : "/";
            apiName = api.getApiName() != null ? api.getApiName() : method + " " + resource;
            storedHeaders = api.getHeaders();
            requestBody = api.getRequestBody();
            fullUrl = api.getUrl();
        } else {
            method = "GET";
            resource = "/";
            apiName = "GET /";
            storedHeaders = null;
            requestBody = null;
            fullUrl = null;
        }

        String name = !apiName.isBlank() && !apiName.startsWith("GET") && !apiName.startsWith("POST")
                ? apiName : method + " " + resource;

        Map<String, Object> itemMap = new LinkedHashMap<>();
        itemMap.put("name", name);
        itemMap.put("description", "历史录制接口\n\n资源路径: " + resource);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", method);

        // URL
        List<Map<String, String>> queryParams = null;
        if (fullUrl != null && !fullUrl.isBlank()) {
            try {
                URI uri = URI.create(fullUrl);
                String q = uri.getQuery();
                if (q != null && !q.isEmpty()) {
                    List<Map<String, String>> qp = new ArrayList<>();
                    for (String param : q.split("&")) {
                        String[] kv = param.split("=", 2);
                        String val = kv.length > 1 ? kv[1] : "";
                        if (val.isEmpty()) continue;
                        qp.add(Map.of("key", kv[0], "value", val));
                    }
                    if (!qp.isEmpty()) queryParams = qp;
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> urlObj = new LinkedHashMap<>();
        urlObj.put("raw", "{{baseUrl}}" + resource);
        if (queryParams != null) urlObj.put("query", queryParams);
        request.put("url", urlObj);

        // Headers
        List<Map<String, String>> headerList = new ArrayList<>();
        if (storedHeaders != null && !storedHeaders.isEmpty()) {
            boolean hasAuth = false;
            for (Map.Entry<String, String> h : storedHeaders.entrySet()) {
                String hk = h.getKey();
                if ("host".equalsIgnoreCase(hk) || "content-length".equalsIgnoreCase(hk)) continue;
                if ("authorization".equalsIgnoreCase(hk)) {
                    hasAuth = true;
                    headerList.add(Map.of("key", hk, "value", "Bearer {{token}}", "description", "请求头"));
                } else if ("content-type".equalsIgnoreCase(hk)) {
                    headerList.add(Map.of("key", "Content-Type", "value", "application/json", "description", "请求头"));
                } else {
                    headerList.add(Map.of("key", hk, "value", h.getValue(), "description", "请求头"));
                }
            }
            if (!hasAuth) {
                headerList.add(Map.of("key", "Authorization", "value", "Bearer {{token}}", "description", "请求头"));
            }
        } else {
            headerList.add(Map.of("key", "Content-Type", "value", "application/json", "description", "请求头"));
            headerList.add(Map.of("key", "Authorization", "value", "Bearer {{token}}", "description", "请求头"));
        }
        request.put("header", headerList);

        // Body
        if (requestBody != null && !requestBody.isBlank() && !"null".equals(requestBody)) {
            Map<String, Object> bodyObj = new LinkedHashMap<>();
            bodyObj.put("mode", "raw");
            try {
                Object parsed = objectMapper.readValue(requestBody.replace('\n', ' ').trim(), Object.class);
                bodyObj.put("raw", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
            } catch (Exception e) {
                bodyObj.put("raw", requestBody);
            }
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("raw", Map.of("language", "json"));
            bodyObj.put("options", options);
            request.put("body", bodyObj);
        }

        itemMap.put("request", request);
        return itemMap;
    }

    // ===== 内部方法 =====

    private String buildTitle(String mainDomain, long timestamp) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(timestamp));
        return (mainDomain != null ? mainDomain : "unknown") + " - " + ts;
    }

    private List<ApiSummary> buildApiSummaries(List<ApiAsset> assets, DedupResult dedupResult) {
        Map<String, ApiAsset> assetMap = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            String key = a.getMethod() + " " + (a.getResource() != null ? a.getResource() : a.getUrl());
            assetMap.putIfAbsent(key, a);
        }

        List<ApiSummary> summaries = new ArrayList<>();
        for (Map<String, Object> dedupApi : dedupResult.getApis()) {
            ApiSummary s = new ApiSummary();
            s.setMethod((String) dedupApi.getOrDefault("method", "GET"));
            s.setResource((String) dedupApi.getOrDefault("resource", "/"));
            Object callCount = dedupApi.get("callCount");
            s.setCallCount(callCount instanceof Number ? ((Number) callCount).intValue() : 1);
            s.setApiName(s.getMethod() + " " + s.getResource());

            String key = s.getMethod() + " " + s.getResource();
            ApiAsset asset = assetMap.get(key);
            if (asset != null) {
                if (asset.getCategory() != null) s.setCategory(asset.getCategory());
                s.setUrl(asset.getUrl());
                s.setRequestBody(asset.getRequestBody());
                s.setHeaders(asset.getHeaders());
            }
            summaries.add(s);
        }
        return summaries;
    }

    private Map<String, String> extractEnvVars(List<ApiAsset> assets) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            if (a.getHeaders() != null) {
                for (Map.Entry<String, String> h : a.getHeaders().entrySet()) {
                    String key = h.getKey().toLowerCase();
                    String value = h.getValue();
                    if (key.equals("authorization") && value.toLowerCase().startsWith("bearer ")) {
                        vars.putIfAbsent("token", value.substring(7));
                    }
                }
            }
            String resBody = a.getResponseBody();
            if (resBody != null) {
                extractFromResponse(resBody, vars);
            }
        }
        return vars;
    }

    private void extractFromResponse(String body, Map<String, String> vars) {
        String[] patterns = {"token", "accessToken", "refreshToken", "userId", "uid"};
        for (String field : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\"" + field + "\"\\s*:\\s*\"([^\"]{4,})\"").matcher(body);
            if (m.find()) {
                vars.putIfAbsent(field, m.group(1));
            }
        }
    }
}
