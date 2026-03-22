package com.ningcai.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final ResourceLoader resourceLoader;

    @Value("${interview.rag.top-k:5}")
    private int topK;

    @Value("${interview.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${interview.materials.chunk-size:1000}")  // 从配置文件读取
    private int chunkSize;

    @Value("${interview.materials.batch-size:10}")    // 从配置文件读取
    private int batchSize;

    /**
     * 加载资料到向量库
     */
    public String loadMaterials(String filePath) {
        try {
            Resource resource = resourceLoader.getResource("classpath:面试相关资料/" + filePath);
            
            if (!resource.exists()) {
                return "文件不存在: " + filePath;
            }

            // 读取文件
            String content = FileCopyUtils.copyToString(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

            // 分片
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < content.length(); i += chunkSize) {
                int end = Math.min(content.length(), i + chunkSize);
                String chunk = content.substring(i, end);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", filePath);
                metadata.put("chunk", i / chunkSize);
                
                documents.add(new Document(chunk, metadata));
            }

            log.info("总共 {} 个片段，分批存入（每批 {} 个）...", documents.size(), batchSize);
            
            // 分批存入（阿里云限制每次最多10个）
            int totalBatches = (int) Math.ceil((double) documents.size() / batchSize);
            
            for (int i = 0; i < documents.size(); i += batchSize) {
                int end = Math.min(documents.size(), i + batchSize);
                List<Document> batch = documents.subList(i, end);
                
                log.info("存入第 {}/{} 批，共 {} 个片段", (i/batchSize + 1), totalBatches, batch.size());
                vectorStore.add(batch);
                
                // 避免请求太频繁
                if (i + batchSize < documents.size()) {
                    Thread.sleep(200);
                }
            }

            return String.format("成功加载资料: %s，共 %d 个片段（分 %d 批存入）", 
                    filePath, documents.size(), totalBatches);
            
        } catch (Exception e) {
            log.error("加载资料失败", e);
            return "加载失败: " + e.getMessage();
        }
    }

    /**
     * 检索资料
     */
    public String search(String question) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(question)
                    .topK(topK)
                    .similarityThreshold((float) similarityThreshold)
                    .build();

            List<Document> results = vectorStore.similaritySearch(request);

            if (results.isEmpty()) {
                return "未找到相关答案";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(results.size()).append(" 个相关片段：\n\n");
            
            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                sb.append("【片段").append(i + 1).append("】\n");
                sb.append("来源: ").append(doc.getMetadata().get("source")).append("\n");
                sb.append("内容: ").append(doc.getText()).append("\n\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("检索失败", e);
            return "检索失败: " + e.getMessage();
        }
    }

    /**
     * RAG 问答
     */
    public String ask(String question) {
        try {
            // 1. 检索
            SearchRequest request = SearchRequest.builder()
                    .query(question)
                    .topK(topK)
                    .similarityThreshold((float) similarityThreshold)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(request);

            if (documents.isEmpty()) {
                return "未找到相关资料来回答这个问题";
            }

            // 2. 构建上下文
            StringBuilder context = new StringBuilder();
            for (Document doc : documents) {
                context.append(doc.getText()).append("\n");
            }

            // 3. 构建提示词
            String prompt = String.format("""
                    基于以下资料回答用户的问题。如果资料中没有相关信息，就说不知道。
                    
                    资料：
                    %s
                    
                    问题：%s
                    
                    回答：
                    """, context, question);

            // 4. 调用 AI
            ChatClient chatClient = chatClientBuilder.build();
            String answer = chatClient.prompt(prompt).call().content();

            return String.format("问题：%s\n\n答案：%s\n\n（基于 %d 个资料片段）", 
                    question, answer, documents.size());

        } catch (Exception e) {
            log.error("问答失败", e);
            return "处理失败: " + e.getMessage();
        }
    }
}