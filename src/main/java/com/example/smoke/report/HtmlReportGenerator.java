package com.example.smoke.report;

import com.example.smoke.model.SmokeTestReport;
import com.example.smoke.model.SmokeTestResult;
import java.util.Map;

/**
 * HTML 格式报告生成器。
 * 生成可独立打开的 HTML 文件，包含统计概览和详细结果。
 */
public class HtmlReportGenerator implements ReportGenerator {

    @Override
    public String generate(SmokeTestReport report) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="UTF-8">
                <title>Smoke Test Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: system-ui, -apple-system, sans-serif; background: #f5f5f5; padding: 40px; color: #333; }
                    .container { max-width: 1200px; margin: 0 auto; }
                    h1 { font-size: 24px; margin-bottom: 8px; }
                    .timestamp { color: #999; font-size: 14px; margin-bottom: 24px; }
                    .summary { display: flex; gap: 20px; margin-bottom: 32px; flex-wrap: wrap; }
                    .summary-card { background: #fff; border-radius: 8px; padding: 20px 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); min-width: 140px; text-align: center; }
                    .summary-card .num { font-size: 32px; font-weight: 700; }
                    .summary-card .label { font-size: 13px; color: #999; margin-top: 4px; }
                    .card-pass .num { color: #52c41a; }
                    .card-fail .num { color: #ff4d4f; }
                    .card-total .num { color: #1677ff; }
                    .card-rate .num { color: #722ed1; }
                    .card-duration .num { color: #d48806; font-size: 24px; }
                    .pass-badge { display: inline-block; padding: 2px 10px; border-radius: 4px; font-size: 12px; font-weight: 600; }
                    .pass-badge.pass { background: #f6ffed; color: #52c41a; }
                    .pass-badge.fail { background: #fff2f0; color: #ff4d4f; }
                    table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
                    th { background: #fafafa; padding: 12px 16px; text-align: left; font-weight: 600; font-size: 13px; color: #666; border-bottom: 1px solid #f0f0f0; }
                    td { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; font-size: 13px; }
                    tr:hover td { background: #fafafa; }
                    .detail-row { display: none; }
                    .detail-row td { background: #f6f8fa; padding: 16px; }
                    .detail-row.open { display: table-row; }
                    .detail-content { font-size: 12px; }
                    .detail-content pre { background: #fff; padding: 8px; border-radius: 4px; max-height: 200px; overflow: auto; margin-top: 6px; white-space: pre-wrap; word-break: break-all; }
                    .method-badge { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; color: #fff; }
                    .method-GET { background: #1677ff; }
                    .method-POST { background: #52c41a; }
                    .method-PUT { background: #d48806; }
                    .method-DELETE { background: #ff4d4f; }
                    .method-PATCH { background: #722ed1; }
                </style>
                </head>
                <body>
                <div class="container">
                """);

        // 标题
        html.append("<h1>").append(escapeHtml(report.getCollectionName())).append("</h1>");
        html.append("<div class=\"timestamp\">执行时间: ").append(escapeHtml(report.getTimestamp())).append("</div>");

        // 统计概览
        html.append("""
                <div class="summary">
                    <div class="summary-card card-total"><div class="num">%d</div><div class="label">总接口</div></div>
                    <div class="summary-card card-pass"><div class="num">%d</div><div class="label">通过</div></div>
                    <div class="summary-card card-fail"><div class="num">%d</div><div class="label">失败</div></div>
                    <div class="summary-card card-rate"><div class="num">%.1f%%</div><div class="label">成功率</div></div>
                    <div class="summary-card card-duration"><div class="num">%dms</div><div class="label">总耗时</div></div>
                </div>
                """.formatted(
                report.getTotal(),
                report.getPassed(),
                report.getFailed(),
                report.getSuccessRate(),
                report.getTotalDurationMs()
        ));

        // 结果表格
        html.append("""
                <table>
                <thead><tr>
                    <th style="width:32px;">#</th>
                    <th style="width:70px;">状态</th>
                    <th style="width:200px;">名称</th>
                    <th style="width:60px;">方法</th>
                    <th>URL</th>
                    <th style="width:60px;">状态码</th>
                    <th style="width:70px;">耗时</th>
                </tr></thead>
                <tbody>
                """);

        int index = 1;
        for (SmokeTestResult r : report.getResults()) {
            String statusClass = r.isPassed() ? "pass" : "fail";
            String statusText = r.isPassed() ? "✓ 通过" : "✗ 失败";

            html.append("<tr onclick=\"toggle(%d)\" style=\"cursor:pointer;\">".formatted(index));
            html.append("<td>").append(index).append("</td>");
            html.append("<td><span class=\"pass-badge ").append(statusClass).append("\">").append(statusText).append("</span></td>");
            html.append("<td>").append(escapeHtml(r.getName())).append("</td>");
            html.append("<td><span class=\"method-badge method-").append(r.getMethod()).append("\">").append(escapeHtml(r.getMethod())).append("</span></td>");
            html.append("<td style=\"word-break:break-all;\">").append(escapeHtml(r.getUrl())).append("</td>");
            String statusColor = r.getHttpStatus() == 200 ? "color:#52c41a;" : "color:#ff4d4f;";
            html.append("<td style=\"").append(statusColor).append("\">").append(r.getHttpStatus()).append("</td>");
            html.append("<td>").append(r.getDurationMs()).append("ms</td>");
            html.append("</tr>");

            // 详情行（默认隐藏，点击展开）
            html.append("<tr class=\"detail-row\" id=\"detail-").append(index).append("\">");
            html.append("<td colspan=\"7\"><div class=\"detail-content\">");

            if (!r.isPassed() && r.getFailureReason() != null) {
                html.append("<strong style=\"color:#ff4d4f;\">失败原因:</strong> ").append(escapeHtml(r.getFailureReason())).append("<br>");
            }
            if (r.getErrorMessage() != null) {
                html.append("<strong>错误信息:</strong> ").append(escapeHtml(r.getErrorMessage())).append("<br>");
            }

            // 显示变量使用情况
            if (r.getUsedVariables() != null && !r.getUsedVariables().isEmpty()) {
                html.append("<strong>使用的变量:</strong><pre style=\"font-size:11px;color:#722ed1;\">");
                for (Map.Entry<String, String> e : r.getUsedVariables().entrySet()) {
                    String val = e.getValue();
                    if (val.length() > 64) val = val.substring(0, 32) + "..." + val.substring(val.length() - 16);
                    html.append(escapeHtml(e.getKey())).append(" = ").append(escapeHtml(val)).append("\n");
                }
                html.append("</pre>");
            }

            // 显示变量提取
            if (r.getExtractedVariables() != null && !r.getExtractedVariables().isEmpty()) {
                html.append("<strong>提取的变量:</strong><pre style=\"font-size:11px;color:#389e0d;\">");
                for (Map.Entry<String, String> e : r.getExtractedVariables().entrySet()) {
                    String val = e.getValue();
                    if (val.length() > 64) val = val.substring(0, 32) + "..." + val.substring(val.length() - 16);
                    html.append(escapeHtml(e.getKey())).append(" = ").append(escapeHtml(val)).append("\n");
                }
                html.append("</pre>");
            }

            if (r.getResponseHeaders() != null && !r.getResponseHeaders().isEmpty()) {
                html.append("<strong>响应头:</strong><pre>").append(escapeHtml(r.getResponseHeaders().toString())).append("</pre>");
            }
            if (r.getResponseBody() != null && !r.getResponseBody().isBlank()) {
                html.append("<strong>响应体:</strong><pre>").append(escapeHtml(r.getResponseBody())).append("</pre>");
            }

            html.append("</div></td></tr>");

            index++;
        }

        html.append("""
                </tbody>
                </table>
                <script>
                function toggle(id) {
                    var row = document.getElementById('detail-' + id);
                    row.classList.toggle('open');
                }
                </script>
                </div>
                </body>
                </html>
                """);

        return html.toString();
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
