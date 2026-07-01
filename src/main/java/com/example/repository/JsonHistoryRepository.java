package com.example.repository;

import com.example.model.HistoryRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JsonHistoryRepository implements HistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonHistoryRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path storageDir = Paths.get("storage", "history");
    private final Path indexPath = storageDir.resolve("index.json");

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageDir);
            if (!Files.exists(indexPath)) {
                Files.writeString(indexPath, "[]");
                log.info("初始化历史记录索引: {}", indexPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("初始化历史存储目录失败", e);
        }
    }

    @Override
    public synchronized void save(HistoryRecord record) {
        try {
            // 写完整记录文件
            File recordFile = getRecordFile(record.getId());
            MAPPER.writeValue(recordFile, record);
            log.debug("历史记录已保存: {}", recordFile.getAbsolutePath());

            // 更新索引
            List<Map<String, Object>> index = readIndex();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", record.getId());
            entry.put("title", record.getTitle());
            entry.put("createdAt", record.getCreatedAt());
            entry.put("apiCount", record.getApiCount());
            // 插入到最前面（最新的在前）
            index.add(0, entry);
            MAPPER.writeValue(indexPath.toFile(), index);

        } catch (IOException e) {
            log.error("保存历史记录失败: id={}", record.getId(), e);
        }
    }

    @Override
    public HistoryRecord findById(String id) {
        File recordFile = getRecordFile(id);
        if (!recordFile.exists()) {
            log.warn("历史记录不存在: {}", id);
            return null;
        }
        try {
            return MAPPER.readValue(recordFile, HistoryRecord.class);
        } catch (IOException e) {
            log.error("读取历史记录失败: id={}", id, e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> findAllIndex() {
        return readIndex();
    }

    @Override
    public synchronized void deleteById(String id) {
        // 删除记录文件
        File recordFile = getRecordFile(id);
        if (recordFile.exists()) {
            boolean deleted = recordFile.delete();
            log.debug("删除历史记录文件: {} → {}", recordFile.getAbsolutePath(), deleted);
        }

        // 更新索引
        List<Map<String, Object>> index = readIndex();
        index.removeIf(entry -> id.equals(entry.get("id")));
        try {
            MAPPER.writeValue(indexPath.toFile(), index);
        } catch (IOException e) {
            log.error("更新索引失败（删除后）: id={}", id, e);
        }
    }

    // ===== 内部辅助 =====

    private File getRecordFile(String id) {
        return storageDir.resolve(id + ".json").toFile();
    }

    private List<Map<String, Object>> readIndex() {
        try {
            if (Files.exists(indexPath)) {
                String content = Files.readString(indexPath);
                if (content.isBlank()) return new ArrayList<>();
                return MAPPER.readValue(content, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (IOException e) {
            log.warn("读取历史索引失败，返回空列表", e);
        }
        return new ArrayList<>();
    }
}
