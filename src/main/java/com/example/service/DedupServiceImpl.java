package com.example.service;

import com.example.ai.filter.ApiPreFilter;
import com.example.ai.parse.AiResultParser;
import com.example.model.ApiAsset;
import com.example.model.DedupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DedupServiceImpl implements DedupService {

    private static final Logger log = LoggerFactory.getLogger(DedupServiceImpl.class);

    private final AiChatService aiChatService;
    private final ApiPreFilter apiPreFilter;
    private final AiResultParser aiResultParser;

    public DedupServiceImpl(AiChatService aiChatService,
                            ApiPreFilter apiPreFilter,
                            AiResultParser aiResultParser) {
        this.aiChatService = aiChatService;
        this.apiPreFilter = apiPreFilter;
        this.aiResultParser = aiResultParser;
    }

    @Override
    public DedupResult dedup(List<ApiAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return new DedupResult(List.of(), 0, false, null);
        }

        // Java 层硬过滤（图片、埋点、静态资源）+ 机械去重
        List<ApiAsset> filtered = apiPreFilter.filter(assets);
        return fallbackDedup(filtered, null);
    }

    private String buildPrompt(List<ApiAsset> assets) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在分析用户在一次浏览器操作中产生的 API 调用列表。\n\n");
        sb.append("【任务】\n");
        sb.append("对以下业务接口进行去重合并。\n\n");
        sb.append("合并规则（按优先级）：\n");
        sb.append("1. 仅 query 参数不同的接口合并为一个\n");
        sb.append("   例: GET /api/list?page=1 和 GET /api/list?page=2 → method=GET, resource=/api/list\n");
        sb.append("2. URL 路径中的 ID 参数（UUID/哈希/数字）视为路径参数，不同 ID 的请求合并为一个\n");
        sb.append("   例: DELETE /api/user/69cf3e7b65d7 和 DELETE /api/user/69e0b310f9da → method=DELETE, resource=/api/user/{id}\n");
        sb.append("   例: PUT /api/topics/123 和 PUT /api/topics/456 → method=PUT, resource=/api/topics/{id}\n");
        sb.append("3. 路径中嵌套 ID 同样处理\n");
        sb.append("   例: POST /api/contents/6a3e5233/status 和 POST /api/contents/6a3e5242/status → method=POST, resource=/api/contents/{id}/status\n\n");
        sb.append("返回 JSON 数组，每个元素格式：\n");
        sb.append("{\n");
        sb.append("  \"method\": \"POST\",\n");
        sb.append("  \"resource\": \"/api/follow\",\n");
        sb.append("  \"apiName\": \"关注用户\",\n");
        sb.append("  \"urlExample\": \"http://xxx/api/follow\",\n");
        sb.append("  \"callCount\": 3,\n");
        sb.append("  \"mergedUrls\": [\"http://xxx/api/follow?page=1\", \"http://xxx/api/follow?page=2\"]\n");
        sb.append("}\n\n");
        sb.append("如果列表为空，返回空数组 []。\n");
        sb.append("只返回 JSON 数组，不要多余内容。\n\n");
        sb.append("API 列表（按捕获顺序）：\n");

        for (int i = 0; i < assets.size(); i++) {
            ApiAsset a = assets.get(i);
            sb.append("[").append(i).append("] ")
                    .append(a.getMethod()).append(" ").append(a.getUrl())
                    .append(" status=").append(a.getStatusCode())
                    .append(" resource=").append(a.getResource())
                    .append(" category=").append(a.getCategory())
                    .append("\n");
        }
        return sb.toString();
    }

    private DedupResult fallbackDedup(List<ApiAsset> assets, String rawAiResponse) {
        // 先对 resource 做归一化：把路径中的 UUID/哈希片段替换为 {id}
        Map<String, ApiAsset> seen = new LinkedHashMap<>();
        Map<String, List<String>> mergedUrls = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            String res = a.getResource() != null ? a.getResource() : a.getUrl();
            String normalized = normalizeResource(res);
            String key = a.getMethod() + " " + normalized;
            seen.putIfAbsent(key, a);
            mergedUrls.computeIfAbsent(key, k -> new ArrayList<>()).add(a.getUrl());
        }
        List<Map<String, Object>> apis = new ArrayList<>();
        for (Map.Entry<String, ApiAsset> entry : seen.entrySet()) {
            ApiAsset a = entry.getValue();
            String key = entry.getKey();
            String normalizedRes = key.substring(key.indexOf(' ') + 1);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("method", a.getMethod());
            item.put("resource", normalizedRes);
            item.put("urlExample", a.getUrl());
            item.put("callCount", mergedUrls.get(key).size());
            if (mergedUrls.get(key).size() > 1) {
                item.put("mergedUrls", mergedUrls.get(key));
            }
            apis.add(item);
        }
        log.info("Dedup 机械去重: {} 项（原始 {} 条）", apis.size(), assets.size());
        return new DedupResult(apis, assets.size(), false, rawAiResponse);
    }

    /** 将 resource path 中的 UUID/长哈希替换为 {id}，用于机械去重 */
    private String normalizeResource(String resource) {
        if (resource == null) return null;
        // 匹配 24 位以上十六进制字符串（MongoDB ObjectId / 哈希值）
        String normalized = resource.replaceAll("/[0-9a-fA-F]{24,}", "/{id}");
        // 匹配 8-23 位十六进制（较短的哈希）
        normalized = normalized.replaceAll("/[0-9a-fA-F]{8,23}(?=/|$)", "/{id}");
        // 匹配纯数字 ID
        normalized = normalized.replaceAll("/\\d{3,}(?=/|$)", "/{id}");
        return normalized;
    }

    /** 从多个候选字段名中提取第一个非空值 */
    private String extractField(Map<String, Object> item, String... candidates) {
        for (String key : candidates) {
            Object val = item.get(key);
            if (val != null && val instanceof String && !((String) val).isBlank()) {
                return (String) val;
            }
        }
        return null;
    }

    private static final java.util.Set<String> HTTP_METHODS = java.util.Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE"
    );

    private boolean isHttpMethod(String method) {
        return method != null && HTTP_METHODS.contains(method);
    }
}
