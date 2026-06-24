package com.example.filter;

import com.example.model.ApiAsset;

import java.util.List;

public interface AssetFilter {
    List<ApiAsset> filter(List<ApiAsset> assets);
    String name();
}
