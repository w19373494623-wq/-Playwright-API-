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

        // Step 1: Java 层硬过滤（图片、埋点、静态资源）
        List<ApiAsset> filtered = apiPreFilter.filter(assets);

        // Step 2: 构建提示词并调用 AI
        String prompt = buildPrompt(filtered);
        String aiResponse;
        try {
            aiResponse = aiChatService.chat("ai-dedup", prompt);
        } catch (Exception e) {
            log.warn("AI 去重调用失败: {}", e.getMessage());
            return fallbackDedup(filtered, null);
        }

        // Step 3: 解析 AI 结果
        List<Map<String, Object>> parsed = aiResultParser.parseArray(aiResponse);
        if (parsed.isEmpty()) {
            return fallbackDedup(filtered, aiResponse);
        }

        // Step 4: 校验字段完整性
        List<Map<String, Object>> valid = new ArrayList<>();
        for (Map<String, Object> item : parsed) {
            if (item.containsKey("method") && item.containsKey("resource")) {
                valid.add(item);
            }
        }

        if (valid.isEmpty()) {
            return fallbackDedup(filtered, aiResponse);
        }

        return new DedupResult(valid, assets.size(), false, aiResponse);
    }

    private String buildPrompt(List<ApiAsset> assets) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在分析用户在一次浏览器操作中产生的 API 调用列表。\n\n");
        sb.append("【任务】\n");
        sb.append("对以下业务接口进行去重合并：\n");
        sb.append("- 相同 method + 相同 resource path 的视为同一接口，只保留一个\n");
        sb.append("- query 参数不同但 resource path 相同的接口合并（如 /api/list?page=1 和 /api/list?page=2 都是 /api/list）\n");
        sb.append("- 路径参数不同的 RESTful 接口视为同一接口（如 /api/user/1 和 /api/user/2 都是 /api/user/{id}）\n");
        sb.append("- 从 url 中提取有意义的 apiName（中文），如 /api/follow → \"关注用户\"\n\n");
        sb.append("返回 JSON 数组，每个元素格式：\n");
        sb.append("{\n");
        sb.append("  \"method\": \"POST\",\n");
        sb.append("  \"resource\": \"/api/follow\",\n");
        sb.append("  \"apiName\": \"关注用户\",\n");
        sb.append("  \"urlExample\": \"http://xxx/api/follow\",\n");
        sb.append("  \"callCount\": 3\n");
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
        Map<String, ApiAsset> seen = new LinkedHashMap<>();
        for (ApiAsset a : assets) {
            String key = a.getMethod() + " " + (a.getResource() != null ? a.getResource() : a.getUrl());
            seen.putIfAbsent(key, a);
        }
        List<Map<String, Object>> apis = new ArrayList<>();
        for (ApiAsset a : seen.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("method", a.getMethod());
            item.put("resource", a.getResource());
            item.put("urlExample", a.getUrl());
            apis.add(item);
        }
        log.warn("Dedup 回退到机械去重: {} 项（原始 {} 条）", apis.size(), assets.size());
        return new DedupResult(apis, assets.size(), true, rawAiResponse);
    }
}
