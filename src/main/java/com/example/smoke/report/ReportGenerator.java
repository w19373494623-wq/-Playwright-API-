package com.example.smoke.report;

import com.example.smoke.model.SmokeTestReport;

/**
 * 测试报告生成器接口。
 * 扩展点：可实现自定义报告格式（如 Allure、JUnit XML、邮件通知等）。
 */
public interface ReportGenerator {

    /**
     * 生成测试报告。
     *
     * @param report 测试结果数据
     * @return 报告内容（文件内容或字符串）
     */
    String generate(SmokeTestReport report);
}
