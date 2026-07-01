package com.example.smoke.assertion;

import com.example.smoke.model.SmokeTestResult;
import com.example.smoke.model.PostmanItem;

/**
 * 断言接口。
 * 扩展点：可实现 Token 自动刷新、自定义断言等。
 */
@FunctionalInterface
public interface Assertion {

    /**
     * 对单个接口的测试结果执行断言。
     *
     * @param item   当前测试的接口条目
     * @param result 测试结果（执行后填充）
     * @return true 通过 / false 失败
     */
    boolean assertApi(PostmanItem item, SmokeTestResult result);
}
