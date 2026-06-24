package com.example.capture;

import com.example.model.ApiAsset;
import com.microsoft.playwright.Page;

import java.util.function.Consumer;

public interface NetworkInterceptor {
    void install(Page page, Consumer<ApiAsset> sink);
    void uninstall(Page page);
}
