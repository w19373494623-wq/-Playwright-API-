package com.example.service;

import com.example.model.ApiAsset;
import com.example.model.DedupResult;
import java.util.List;

public interface DedupService {
    DedupResult dedup(List<ApiAsset> assets);
}
