package com.example.repository;

import com.example.model.HistoryRecord;

import java.util.List;
import java.util.Map;

public interface HistoryRepository {
    void save(HistoryRecord record);
    HistoryRecord findById(String id);
    List<Map<String, Object>> findAllIndex();
    void deleteById(String id);
}
