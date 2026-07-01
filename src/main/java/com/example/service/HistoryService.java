package com.example.service;

import com.example.model.ApiAsset;
import com.example.model.ApiSummary;
import com.example.model.DedupResult;
import com.example.model.HistoryRecord;
import com.example.repository.HistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

        historyRepository.save(record);
        log.info("历史记录已保存: id={}, title={}, apis={}",
                id, record.getTitle(), apiSummaries.size());
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

    // ===== 内部方法 =====

    private String buildTitle(String mainDomain, long timestamp) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(timestamp));
        return (mainDomain != null ? mainDomain : "unknown") + " - " + ts;
    }

    private List<ApiSummary> buildApiSummaries(List<ApiAsset> assets, DedupResult dedupResult) {
        // 构建 method+resource → ApiAsset 映射（获取 category）
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
            if (asset != null && asset.getCategory() != null) {
                s.setCategory(asset.getCategory());
            }
            summaries.add(s);
        }
        return summaries;
    }
}
