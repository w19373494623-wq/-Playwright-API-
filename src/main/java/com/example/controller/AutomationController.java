package com.example.controller;

import com.example.model.AutomationConfig;
import com.example.service.AutomationConfigService;
import com.example.service.AutomationService;
import com.example.smoke.model.SmokeTestReport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动化执行控制器（第一版）。
 *
 * POST /automation/run/{historyId}
 *   - 根据历史记录执行烟雾测试
 *   - 完全复用 SmokeTestService（解析/执行/断言/报告）
 *   - 暂不包含登录、Token 刷新、变量替换
 *
 * GET  /automation/config
 * POST /automation/config
 *   - 自动化配置的读取和保存
 */
@RestController
public class AutomationController {

    private final AutomationService automationService;
    private final AutomationConfigService automationConfigService;

    public AutomationController(AutomationService automationService,
                                AutomationConfigService automationConfigService) {
        this.automationService = automationService;
        this.automationConfigService = automationConfigService;
    }

    /**
     * 根据历史记录执行自动化烟雾测试。
     */
    @PostMapping("/automation/run/{historyId}")
    public ResponseEntity<SmokeTestReport> run(@PathVariable String historyId) {
        SmokeTestReport report = automationService.run(historyId);
        return ResponseEntity.ok(report);
    }

    /**
     * 获取自动化配置。
     */
    @GetMapping("/automation/config")
    public ResponseEntity<AutomationConfig> getConfig() {
        return ResponseEntity.ok(automationConfigService.getConfig());
    }

    /**
     * 保存自动化配置。
     */
    @PostMapping("/automation/config")
    public ResponseEntity<AutomationConfig> saveConfig(@RequestBody AutomationConfig config) {
        automationConfigService.saveConfig(config);
        return ResponseEntity.ok(automationConfigService.getConfig());
    }
}
