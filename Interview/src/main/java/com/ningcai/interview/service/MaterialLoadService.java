package com.ningcai.interview.service;

import com.ningcai.interview.model.enums.InterviewRound;
import com.ningcai.interview.model.enums.PositionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialLoadService {

    private final VectorStore vectorStore;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Value("${interview.materials.base-path:classpath:面试相关资料/}")
    private String basePath;

    @Value("${interview.materials.chunk-size:1000}")
    private int chunkSize;

    @Value("${interview.materials.batch-size:10}")
    private int batchSize;

    /**
     * 加载指定岗位的所有资料
     */
    public String loadPositionMaterials(PositionType position) throws Exception {
        String positionPath = getPositionPath(position);
        Resource[] resources = resolver.getResources(basePath + positionPath + "/**/*.md");
        
        StringBuilder result = new StringBuilder();
        result.append("开始加载").append(position.getDesc()).append("岗位资料...\n\n");
        
        int totalFiles = 0;
        int totalChunks = 0;
        
        for (Resource resource : resources) {
            if (resource.exists()) {
                String filePath = extractPath(resource);
                LoadResult loadResult = loadSingleFile(resource, filePath, position);
                result.append(loadResult.getMessage()).append("\n");
                totalFiles++;
                totalChunks += loadResult.getChunkCount();
                
                // 避免请求太频繁
                Thread.sleep(200);
            }
        }
        
        result.append(String.format("\n✅ 加载完成！共加载 %d 个文件，%d 个片段", totalFiles, totalChunks));
        return result.toString();
    }

    /**
     * 加载单个文件
     */
    private LoadResult loadSingleFile(Resource resource, String filePath, PositionType position) throws Exception {
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        // 判断是第几面还是题库
        InterviewRound round = InterviewRound.fromPath(filePath);
        
        List<Document> documents = new ArrayList<>();
        
        // 分片处理
        for (int i = 0; i < content.length(); i += chunkSize) {
            int end = Math.min(content.length(), i + chunkSize);
            String chunk = content.substring(i, end);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", filePath);
            metadata.put("position", position.getCode());
            metadata.put("round", round.getCode());
            metadata.put("roundDesc", round.getDesc());
            metadata.put("chunk", i / chunkSize);
            metadata.put("filename", resource.getFilename());
            
            documents.add(new Document(chunk, metadata));
        }
        
        // 分批存入
        int totalBatches = (int) Math.ceil((double) documents.size() / batchSize);
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(documents.size(), i + batchSize);
            vectorStore.add(documents.subList(i, end));
        }
        
        String roundInfo = round == InterviewRound.GENERAL ? "题库" : round.getDesc();
        return new LoadResult(
            String.format("✅ %s - %s: %d 个片段", roundInfo, resource.getFilename(), documents.size()),
            documents.size()
        );
    }

    /**
     * 加载所有岗位的所有资料
     */
    public String loadAllMaterials() throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("🚀 开始加载所有面试资料...\n\n");
        
        for (PositionType position : PositionType.values()) {
            try {
                String loadResult = loadPositionMaterials(position);
                result.append(loadResult).append("\n");
                result.append("=".repeat(50)).append("\n\n");
                Thread.sleep(1000);
            } catch (Exception e) {
                result.append("❌ 加载").append(position.getDesc()).append("失败: ").append(e.getMessage()).append("\n");
            }
        }
        
        return result.toString();
    }

    /**
     * 根据岗位获取路径
     */
    private String getPositionPath(PositionType position) {
        switch (position) {
            case JAVA: return "java后端面试";
            case PYTHON: return "python工程师面试";
            case WEB: return "Web前端面试";
            case TEST: return "测试面试";
            case OPS: return "运维面试";
            default: return "";
        }
    }

    /**
     * 从Resource中提取相对路径
     */
    private String extractPath(Resource resource) {
        String fullPath = resource.getDescription();
        int index = fullPath.indexOf("面试相关资料/");
        if (index != -1) {
            return fullPath.substring(index + 7); // "面试相关资料/".length() = 7
        }
        return resource.getFilename();
    }

    /**
     * 加载结果内部类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class LoadResult {
        private String message;
        private int chunkCount;
    }
}