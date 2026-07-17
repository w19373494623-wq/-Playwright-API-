package com.example.smoke.controller;

import com.example.smoke.model.SmokeTestReport;
import com.example.smoke.service.SmokeTestService;
import com.example.model.SmokeTestSummary;
import com.example.service.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 烟雾测试控制器。
 * 提供运行测试、查看报告（JSON/HTML）的 REST 接口。
 */
@RestController
@RequestMapping("/smoke")
public class SmokeTestController {

    private static final Logger log = LoggerFactory.getLogger(SmokeTestController.class);

    private final SmokeTestService smokeTestService;
    private final HistoryService historyService;

    public SmokeTestController(SmokeTestService smokeTestService,
                                HistoryService historyService) {
        this.smokeTestService = smokeTestService;
        this.historyService = historyService;
    }

    /**
     * 执行烟雾测试。
     * 请求体：Postman Collection v2.1 JSON 字符串。
     *
     * @param historyId 可选，指定后测试完成后自动持久化新 token 到历史记录
     */
    @PostMapping(value = "/run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> runSmokeTest(@RequestBody String collectionJson,
                                           @RequestParam(required = false) String historyId) {
        log.info("收到烟雾测试请求" + (historyId != null ? ", historyId=" + historyId : ""));

        SmokeTestReport report = smokeTestService.runSmokeTest(collectionJson);

        // 测试完成后，若有 historyId，保存结果摘要到历史记录
        if (historyId != null) {
            // 持久化新 token
            if (report.getLatestVars() != null) {
                for (Map.Entry<String, String> entry : report.getLatestVars().entrySet()) {
                    historyService.updateToken(historyId, entry.getKey(), entry.getValue());
                }
            }
            // 保存测试结果摘要
            SmokeTestSummary summary = new SmokeTestSummary();
            summary.setTotal(report.getTotal());
            summary.setPassed(report.getPassed());
            summary.setFailed(report.getFailed());
            summary.setSuccessRate(report.getSuccessRate());
            summary.setDuration(report.getTotalDurationMs());
            summary.setExecuteTime(report.getTimestamp());
            historyService.updateSmokeResult(historyId, summary);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", report.getErrorMessage() == null);
        result.put("collectionName", report.getCollectionName());
        result.put("total", report.getTotal());
        result.put("passed", report.getPassed());
        result.put("failed", report.getFailed());
        result.put("successRate", String.format("%.1f%%", report.getSuccessRate()));
        result.put("totalDurationMs", report.getTotalDurationMs());
        result.put("timestamp", report.getTimestamp());
        if (report.getErrorMessage() != null) {
            result.put("error", report.getErrorMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取最后一次测试的 JSON 报告
     */
    @GetMapping("/report")
    public ResponseEntity<?> getReport() {
        SmokeTestReport report = smokeTestService.getLastReport();
        if (report == null) {
            return ResponseEntity.ok(Map.of("error", "尚无测试报告"));
        }
        return ResponseEntity.ok(report);
    }

    /**
     * 获取最后一次测试的 HTML 报告
     */
    @GetMapping("/report/html")
    public ResponseEntity<String> getHtmlReport() {
        String html = smokeTestService.getHtmlReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8")
                .body(html);
    }

    /**
     * 列出所有已保存的烟雾测试报告
     */
    @GetMapping("/reports")
    public ResponseEntity<?> listReports() {
        return ResponseEntity.ok(Map.of("reports", smokeTestService.listReports()));
    }

    /**
     * 获取指定报告文件内容（HTML 或 JSON）
     */
    @GetMapping("/report/file/{fileName:.+}")
    public ResponseEntity<?> getReportFile(@PathVariable String fileName) {
        try {
            Path file = Paths.get("storage", "smoke-reports", fileName)
                    .normalize();
            if (!Files.exists(file) || !file.startsWith(Paths.get("storage", "smoke-reports"))) {
                return ResponseEntity.notFound().build();
            }
            String content = Files.readString(file);
            String contentType = fileName.endsWith(".html")
                    ? "text/html;charset=UTF-8"
                    : "application/json;charset=UTF-8";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(content);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
