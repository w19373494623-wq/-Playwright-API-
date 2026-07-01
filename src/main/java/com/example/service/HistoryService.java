package com.example.service;

import com.example.model.ApiAsset;
import com.example.model.ApiSummary;
import com.example.model.DedupResult;
import com.example.model.HistoryRecord;
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
     * 第一阶段保存：Filter + Dedup 后立即保存，不依赖 AI。
     *
     * @param assets     过滤后的完整资产列表
     * @param mainDomain 主域名
     * @param pageUrl    录制目标网址
     * @param startTime  录制开始时间戳
     * @return 已保存的 HistoryRecord
     */
    public HistoryRecord save(List<ApiAsset> assets,
                              String mainDomain, String pageUrl, long startTime) {
        // 机械去重
        DedupResult dedupResult = dedupService.dedup(assets);

        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        HistoryRecord record = new HistoryRecord();
        record.setId(id);
        record.setTitle(buildTitle(mainDomain, now));
        record.setUrl(pageUrl);
        record.setMainDomain(mainDomain);
        record.setCreatedAt(now);
        record.setDuration((now - startTime) / 1000);
        record.setTotalRaw(dedupResult.getTotalRaw());
        record.setTotalFiltered(assets.size());
        record.setApiCount(dedupResult.getTotal());

        // 构建精简 API 列表
        List<ApiSummary> apiSummaries = buildApiSummaries(assets, dedupResult);
        record.setApis(apiSummaries);

        // 提取环境变量并保存
        record.setEnvVars(extractEnvVars(assets));

        historyRepository.save(record);
        log.info("历史记录已保存: id={}, title={}, apis={}, envVars={}",
                id, record.getTitle(), apiSummaries.size(), record.getEnvVars().size());
        return record;
    }

    /**
     * 第二阶段更新：AI 分析成功后回填 summary / businessFlow。
     */
    public void updateAiResult(String id, String summary, Object businessFlow) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新AI结果失败，历史记录不存在: id={}", id);
            return;
        }
        if (summary != null) record.setSummary(summary);
        if (businessFlow != null) record.setBusinessFlow(businessFlow);
        historyRepository.save(record);
        log.info("历史记录AI结果已更新: id={}", id);
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
        historyRepository.save(record);
        log.info("历史记录标题已更新: id={}, title={}", id, newTitle);
    }

    /**
     * 更新历史记录环境变量。
     */
    public void updateEnvVars(String id, Map<String, String> envVars) {
        HistoryRecord record = historyRepository.findById(id);
        if (record == null) {
            log.warn("更新环境变量失败，历史记录不存在: id={}", id);
            return;
        }
        record.setEnvVars(envVars);
        historyRepository.save(record);
        log.info("历史记录环境变量已更新: id={}, vars={}", id, envVars.keySet());
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 HistoryRecord 转为 Postman Collection v2.1 JSON 字符串。
     * 供 AutomationService 调用，复用 SmokeTestService 执行烟雾测试。
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

        // group by module
        List<ApiSummary> apis = record.getApis();
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        if (apis != null) {
            for (ApiSummary api : apis) {
                String module = inferModuleFromUrl(api.getResource());
                grouped.computeIfAbsent(module, k -> new ArrayList<>()).add(buildItemMap(api, baseUrl));
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

    private Map<String, Object> buildItemMap(ApiSummary api, String baseUrl) {
        String method = api.getMethod() != null ? api.getMethod() : "GET";
        String resource = api.getResource() != null ? api.getResource() : "/";
        String apiName = api.getApiName() != null ? api.getApiName() : method + " " + resource;
        String name = !apiName.isBlank() && !apiName.startsWith("GET") && !apiName.startsWith("POST")
                ? apiName : method + " " + resource;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("description", "历史录制接口\n\n资源路径: " + resource);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", method);

        // URL
        String fullUrl = api.getUrl();
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
        Map<String, String> storedHeaders = api.getHeaders();
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
        String requestBody = api.getRequestBody();
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

        item.put("request", request);
        return item;
    }

    // ===== 内部方法 =====

    private String buildTitle(String mainDomain, long timestamp) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(timestamp));
        return (mainDomain != null ? mainDomain : "unknown") + " - " + ts;
    }

    private List<ApiSummary> buildApiSummaries(List<ApiAsset> assets, DedupResult dedupResult) {
        // 构建 method+resource → ApiAsset 映射
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

    /**
     * 从 ApiAsset 列表中提取环境变量（token, userId 等）。
     */
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
            // 从响应体提取常见字段
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
