package com.ningcai.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ningcai.interview.model.dto.EvaluationDTO;
import com.ningcai.interview.model.dto.InterviewSessionDTO;
import com.ningcai.interview.model.dto.InterviewSummaryDTO;
import com.ningcai.interview.model.enums.InterviewRound;
import com.ningcai.interview.model.enums.PositionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${interview.rag.top-k:3}")
    private int topK;

    @Value("${interview.rag.similarity-threshold:0.5}")
    private double similarityThreshold;

    // 缓存 - 改为存储问题列表，支持随机返回
    private final Map<String, List<String>> questionCache = new ConcurrentHashMap<>();
    private final Map<String, EvaluationDTO> evaluationCache = new ConcurrentHashMap<>();
    private final Map<String, String> firstQuestionCache = new ConcurrentHashMap<>();

    /**
     * 1. 生成面试题（带缓存，随机返回）
     */
    public String generateQuestion(PositionType position, InterviewRound round) {
        String cacheKey = position.getCode() + "_" + round.getCode();

        // 1. 查缓存，如果有多个问题，随机返回一个
        List<String> cachedQuestions = questionCache.get(cacheKey);
        if (cachedQuestions != null && !cachedQuestions.isEmpty()) {
            String randomQuestion = cachedQuestions.get(ThreadLocalRandom.current().nextInt(cachedQuestions.size()));
            log.info("命中问题缓存: {}, 共{}个问题，随机返回: {}", cacheKey, cachedQuestions.size(), randomQuestion);
            return randomQuestion;
        }

        long start = System.currentTimeMillis();
        try {
            // 2. 随机检索
            String randomQuery = getRandomQuery(position);
            SearchRequest request = SearchRequest.builder()
                    .query(randomQuery)
                    .topK(3)
                    .filterExpression(String.format("position == '%s' && round == '%s'",
                            position.getCode(), round.getCode()))
                    .build();

            List<Document> documents = vectorStore.similaritySearch(request);
            String context = documents.isEmpty() ? "" :
                    documents.stream()
                            .map(Document::getText)
                            .map(text -> text.length() > 300 ? text.substring(0, 300) + "..." : text)
                            .collect(Collectors.joining("\n"));

            // 3. 生成多个问题
            String prompt = String.format("""
                你是一位%s面试官（%s），请生成5个不同的面试题。
                
                参考资料：%s
                
                要求：
                1. 必须生成5个问题，不能少！
                2. 每个问题要不同方向，涵盖不同知识点
                3. 问题要具体、清晰
                4. 每行一个问题，不要编号
                5. 不要重复相同类型的问题
                6. 问题长度控制在15-25字左右
                
                请直接输出5个问题，每行一个：
                """,
                    position.getDesc(), round.getDesc(),
                    context.isEmpty() ? "无" : context);

            ChatClient chatClient = chatClientBuilder.build();
            String questionsText = chatClient.prompt(prompt).call().content();

            // 4. 解析问题列表
            List<String> questions = new ArrayList<>();
            if (questionsText != null) {
                String[] lines = questionsText.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    // 去掉编号
                    if (line.matches("^\\d+[\\s\\.、].*")) {
                        line = line.replaceAll("^\\d+[\\s\\.、]", "").trim();
                    }
                    if (line.startsWith("- ") || line.startsWith("* ")) {
                        line = line.substring(2).trim();
                    }
                    if (!line.isEmpty()) {
                        questions.add(line);
                    }
                }
            }

            // 5. 确保至少有3个问题
            if (questions.isEmpty()) {
                questions.add(getFallbackQuestion(position, round));
            } else if (questions.size() < 3) {
                questions.add(getFallbackQuestion(position, round));
            }

            // 6. 存入缓存
            questionCache.put(cacheKey, questions);
            log.info("生成问题完成，共生成{}个问题，耗时: {}ms", questions.size(), System.currentTimeMillis() - start);

            // 7. 随机返回一个问题
            String result = questions.get(ThreadLocalRandom.current().nextInt(questions.size()));
            log.info("返回随机问题: {}", result);
            return result;

        } catch (Exception e) {
            log.error("生成问题失败", e);
            return getFallbackQuestion(position, round);
        }
    }
    /**
     * 2. 评估回答（带缓存）- 内部评分，不给用户看
     */
    public EvaluationDTO evaluateAnswer(PositionType position, InterviewRound round,
                                        String userAnswer, String questionId) {
        // 安全检查
        if (userAnswer == null) {
            userAnswer = "";
        }

        String cacheKey = position.getCode() + "_" + round.getCode() + "_" +
                questionId + "_" + userAnswer.hashCode();

        // 1. 查缓存
        EvaluationDTO cached = evaluationCache.get(cacheKey);
        if (cached != null) {
            log.debug("命中评估缓存");
            return cached;
        }

        long start = System.currentTimeMillis();
        try {
            // 2. 检索参考
            SearchRequest request = SearchRequest.builder()
                    .query(userAnswer)
                    .topK(2)
                    .filterExpression(String.format("position == '%s' && round == '%s'",
                            position.getCode(), round.getCode()))
                    .build();

            List<Document> documents = vectorStore.similaritySearch(request);
            String reference = documents.isEmpty() ? "无参考标准" :
                    documents.stream()
                            .map(Document::getText)
                            .map(text -> text.length() > 300 ? text.substring(0, 300) + "..." : text)
                            .collect(Collectors.joining("\n"));

            // 3. 精简提示词 - 只评分，不问下一个问题
            String prompt = String.format("""
                你是一位专业的%s岗位面试官，正在进行%s面试。
                
                请评估求职者的回答，给出分数和简要评价（这是内部评估，不要问下一个问题）。
                
                问题ID：%s
                求职者回答：%s
                参考答案：%s
                
                请以严格的JSON格式输出，不要包含任何其他文字：
                {
                    "score": 0-100的整数,
                    "feedback": "一句话简要评价",
                    "strengths": ["优点1"]或[],
                    "weaknesses": ["不足1"]或[],
                    "suggestions": ["建议1"]或[]
                }
                
                要求：
                1. 分数要合理，根据回答质量给出
                2. feedback要具体，20字左右
                3. 只返回JSON，不要加```markdown```标记
                """,
                    position.getDesc(),
                    round.getDesc(),
                    questionId,
                    userAnswer.length() > 200 ? userAnswer.substring(0, 200) + "..." : userAnswer,
                    reference);

            // 4. 调用AI
            ChatClient chatClient = chatClientBuilder.build();
            String evaluationJson = chatClient.prompt(prompt).call().content();

            // 5. 清理JSON（移除可能的markdown标记）
            if (evaluationJson != null) {
                evaluationJson = evaluationJson.replaceAll("(?s)```json\\s*|```", "").trim();
            }

            // 6. 解析JSON
            EvaluationDTO result = parseEvaluation(evaluationJson);

            // 7. 存入缓存
            evaluationCache.put(cacheKey, result);
            if (evaluationCache.size() > 500) {
                evaluationCache.clear();
            }

            log.info("评估完成，得分: {}，耗时: {}ms", result.getScore(), System.currentTimeMillis() - start);
            return result;

        } catch (Exception e) {
            log.error("评估失败，耗时: {}ms", System.currentTimeMillis() - start, e);
            return getDefaultEvaluation();
        }
    }

    /**
     * 3. 生成下一个问题（改进版）- 修复"请继续说下去"的问题
     */
    public String generateNextQuestion(PositionType position, InterviewRound round,
                                       String previousAnswer, String history) {

        // 安全检查
        if (previousAnswer == null) {
            previousAnswer = "";
        }
        if (history == null) {
            history = "";
        }

        // 1. 基础校验
        String trimmedAnswer = previousAnswer.trim();

        if (trimmedAnswer.isEmpty()) {
            log.debug("回答为空，使用追问");
            return getSmartFollowUp(position, round, "用户没有回答");
        }

        // 2. 检查是否是无效回答（不知道、不清楚等）
        if (isInvalidAnswer(trimmedAnswer)) {
            log.debug("检测到无效回答: {}", trimmedAnswer);
            return getEncouragingQuestion(position);
        }

        // 3. 检查是否太短（改为3个字，原先是20字）
        if (trimmedAnswer.length() < 3) {
            log.debug("回答太短: {}字，使用引导性问题", trimmedAnswer.length());
            return getFollowUpQuestion(position);
        }

        // 4. 正常生成下一题
        long start = System.currentTimeMillis();
        try {
            String prompt = String.format("""
                你是一位%s面试官（%s），正在面试一位求职者。
                
                求职者的回答：%s
                之前的对话历史：%s
                
                请基于这个回答，提出一个自然的下一个问题。
                
                要求：
                1. 如果回答正确或不完整，可以追问细节
                2. 如果回答偏离主题，可以引导回来
                3. 如果回答太简单，可以加深难度
                4. 问题要具体、自然，15-20字左右
                5. 不要问"请继续说下去"这种无意义的问题
                
                下一个问题：
                """,
                    position.getDesc(),
                    round.getDesc(),
                    trimmedAnswer.length() > 100 ? trimmedAnswer.substring(0, 100) + "..." : trimmedAnswer,
                    history.length() > 200 ? history.substring(0, 200) + "..." : history);

            ChatClient chatClient = chatClientBuilder.build();
            String question = chatClient.prompt(prompt).call().content();

            log.info("生成下一个问题成功，耗时: {}ms", System.currentTimeMillis() - start);
            return question != null ? question : getFallbackQuestion(position, round);

        } catch (Exception e) {
            log.error("生成下一个问题失败，使用备用问题", e);
            return getFallbackQuestion(position, round);
        }
    }

    @Async
    public CompletableFuture<InterviewSummaryDTO> generateSummaryAsync(InterviewSessionDTO session) {
        long start = System.currentTimeMillis();
        try {
            // 构建历史
            StringBuilder historyText = new StringBuilder();
            if (session.getHistory() != null) {
                for (InterviewSessionDTO.QAPair pair : session.getHistory()) {
                    historyText.append("Q:").append(pair.getQuestion()).append("\n");
                    historyText.append("A:").append(pair.getUserAnswer()).append("\n");
                }
            }

            String prompt = String.format("""
                    根据面试记录生成总结：
                    岗位：%s，轮次：%s
                    
                    记录：
                    %s
                    
                    返回JSON格式：
                    {"totalScore":0-100, "strengths":["优点1"], "weaknesses":["不足1"], "suggestions":["建议1"]}
                    """,
                    session.getPosition(), session.getRound(),
                    historyText.length() > 500 ? historyText.substring(0, 500) + "..." : historyText);

            ChatClient chatClient = chatClientBuilder.build();
            String summaryJson = chatClient.prompt(prompt).call().content();

            InterviewSummaryDTO summary = parseSummary(summaryJson);
            summary.setTotalQuestions(session.getHistory() != null ? session.getHistory().size() : 0);

            log.info("生成总结耗时: {}ms", System.currentTimeMillis() - start);
            return CompletableFuture.completedFuture(summary);

        } catch (Exception e) {
            log.error("生成总结失败", e);
            return CompletableFuture.completedFuture(getDefaultSummary());
        }
    }

    /**
     * 同步生成总结（用于快速返回）- 修复版：根据实际得分决定下一轮
     */
    public InterviewSummaryDTO generateQuickSummary(InterviewSessionDTO session) {
        try {
            // 1. 构建历史对话
            StringBuilder historyText = new StringBuilder();
            int totalScore = 0;
            List<String> allStrengths = new ArrayList<>();
            List<String> allWeaknesses = new ArrayList<>();

            if (session.getHistory() != null && !session.getHistory().isEmpty()) {
                for (InterviewSessionDTO.QAPair pair : session.getHistory()) {
                    historyText.append("问题：").append(pair.getQuestion()).append("\n");
                    historyText.append("回答：").append(pair.getUserAnswer()).append("\n");
                    if (pair.getEvaluation() != null) {
                        historyText.append("得分：").append(pair.getEvaluation().getScore()).append("\n");
                        totalScore += pair.getEvaluation().getScore();

                        // 收集优点和不足
                        if (pair.getEvaluation().getStrengths() != null) {
                            allStrengths.addAll(pair.getEvaluation().getStrengths());
                        }
                        if (pair.getEvaluation().getWeaknesses() != null) {
                            allWeaknesses.addAll(pair.getEvaluation().getWeaknesses());
                        }
                    }
                    historyText.append("\n");
                }
            } else {
                historyText.append("暂无回答记录");
            }

            // 2. 计算平均分
            int answeredCount = session.getHistory() != null ? session.getHistory().size() : 0;
            int avgScore = answeredCount > 0 ? totalScore / answeredCount : 0;

            // 3. 根据岗位和得分生成总结
            String prompt = String.format("""
            你是一位专业的%s岗位面试官。
            
            请根据以下面试记录，生成一份详细的面试总结：
            
            岗位：%s
            轮次：%s
            平均得分：%d分
            
            面试记录：
            %s
            
            请以JSON格式返回，包含以下字段：
            {
                "totalScore": 总分（0-100，使用给定的平均得分）,
                "strengths": ["优势1", "优势2"],  // 2-3个具体优势
                "weaknesses": ["不足1", "不足2"],  // 2-3个具体不足
                "suggestions": ["建议1", "建议2"]  // 2-3个具体学习建议
            }
            
            要求：
            1. 总分直接使用 %d 分
            2. 优势和不足要针对具体的回答内容
            3. 建议要针对%s岗位的特点
            4. 只返回JSON，不要其他文字
            """,
                    session.getPosition(),
                    session.getPosition(),
                    session.getRound(),
                    avgScore,
                    historyText.toString(),
                    avgScore,
                    session.getPosition());

            ChatClient chatClient = chatClientBuilder.build();
            String summaryJson = chatClient.prompt(prompt).call().content();

            // 4. 解析JSON
            if (summaryJson != null && summaryJson.contains("```")) {
                summaryJson = summaryJson.replaceAll("(?s)```json\\s*|```", "").trim();
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(summaryJson);

            InterviewSummaryDTO dto = new InterviewSummaryDTO();
            dto.setTotalQuestions(answeredCount);
            dto.setDuration(answeredCount * 3); // 估算用时
            dto.setTotalScore(avgScore);
            dto.setCorrectRate(avgScore + "%");

            // 解析优势、不足和建议
            dto.setStrengths(parseJsonArray(root.path("strengths"),
                    !allStrengths.isEmpty() ? allStrengths : List.of("基础概念清晰")));
            dto.setWeaknesses(parseJsonArray(root.path("weaknesses"),
                    !allWeaknesses.isEmpty() ? allWeaknesses : List.of("需要加强实践")));
            dto.setSuggestions(parseJsonArray(root.path("suggestions"),
                    getDefaultSuggestions(session.getPosition(), avgScore)));

            // 根据得分决定下一轮（这里只是占位，实际由Controller决定）
            dto.setNextRound(determineNextRoundByScore(session.getRound(), avgScore));

            return dto;

        } catch (Exception e) {
            log.error("生成AI总结失败，使用备用方案", e);
            return getFallbackSummary(session);
        }
    }

    /**
     * 备用总结方案（当AI失败时使用）
     */
    private InterviewSummaryDTO getFallbackSummary(InterviewSessionDTO session) {
        InterviewSummaryDTO dto = new InterviewSummaryDTO();
        int answeredCount = session.getHistory() != null ? session.getHistory().size() : 0;
        dto.setTotalQuestions(answeredCount);
        dto.setDuration(answeredCount * 3);

        // 计算平均分
        int totalScore = 0;
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();

        if (session.getHistory() != null) {
            for (InterviewSessionDTO.QAPair pair : session.getHistory()) {
                if (pair.getEvaluation() != null) {
                    totalScore += pair.getEvaluation().getScore();

                    // 收集已有的评价
                    if (pair.getEvaluation().getStrengths() != null) {
                        strengths.addAll(pair.getEvaluation().getStrengths());
                    }
                    if (pair.getEvaluation().getWeaknesses() != null) {
                        weaknesses.addAll(pair.getEvaluation().getWeaknesses());
                    }
                }
            }
        }

        int avgScore = answeredCount > 0 ? totalScore / answeredCount : 70;
        dto.setTotalScore(avgScore);
        dto.setCorrectRate(avgScore + "%");

        // 根据得分设置评价
        if (avgScore >= 85) {
            dto.setStrengths(strengths.isEmpty() ? List.of("技术扎实", "表达清晰") : strengths);
            dto.setWeaknesses(weaknesses.isEmpty() ? List.of("可以挑战更高难度") : weaknesses);
            dto.setSuggestions(getDefaultSuggestions(session.getPosition(), avgScore));
        } else if (avgScore >= 70) {
            dto.setStrengths(strengths.isEmpty() ? List.of("基础较好", "理解能力不错") : strengths);
            dto.setWeaknesses(weaknesses.isEmpty() ? List.of("深度有待加强") : weaknesses);
            dto.setSuggestions(getDefaultSuggestions(session.getPosition(), avgScore));
        } else {
            dto.setStrengths(strengths.isEmpty() ? List.of("态度积极") : strengths);
            dto.setWeaknesses(weaknesses.isEmpty() ? List.of("基础知识不牢固") : weaknesses);
            dto.setSuggestions(getDefaultSuggestions(session.getPosition(), avgScore));
        }

        // 根据得分决定下一轮
        dto.setNextRound(determineNextRoundByScore(session.getRound(), avgScore));

        return dto;
    }

    /**
     * 根据得分获取默认建议
     */
    private List<String> getDefaultSuggestions(String position, int score) {
        String pos = position.toLowerCase();
        if (score >= 85) {
            if (pos.contains("java")) {
                return List.of("深入学习JVM调优", "研究并发编程源码", "阅读Spring源码");
            } else if (pos.contains("python")) {
                return List.of("学习异步编程", "深入研究Django/Flask源码", "掌握性能优化技巧");
            } else if (pos.contains("web")) {
                return List.of("学习框架源码", "研究性能优化", "掌握工程化实践");
            }
        } else if (score >= 70) {
            if (pos.contains("java")) {
                return List.of("加强JVM内存模型理解", "多练习多线程编程", "复习常用设计模式");
            } else if (pos.contains("python")) {
                return List.of("深入学习装饰器", "掌握异步编程", "多做项目实践");
            } else if (pos.contains("web")) {
                return List.of("理解框架原理", "多写组件", "学习性能优化");
            }
        } else {
            if (pos.contains("java")) {
                return List.of("复习Java基础语法", "理解面向对象思想", "多写代码练习");
            } else if (pos.contains("python")) {
                return List.of("巩固基础语法", "理解函数式编程", "多练习算法题");
            } else if (pos.contains("web")) {
                return List.of("掌握HTML/CSS基础", "理解JavaScript核心", "多做小项目");
            }
        }
        return List.of("继续加油", "多练习", "多总结");
    }

    /**
     * 根据得分决定下一轮（辅助方法）
     */
    private String determineNextRoundByScore(String currentRound, int score) {
        if (score < 60) {
            return "failed"; // 不及格，需要重考
        }

        return switch (currentRound.toLowerCase()) {
            case "first" -> (score >= 80) ? "second" : "first_retry";
            case "second" -> (score >= 75) ? "third" : "second_retry";
            case "third" -> (score >= 70) ? "fourth" : "third_retry";
            case "fourth" -> (score >= 70) ? "passed" : "fourth_retry";
            default -> "general";
        };
    }

    /**
     * 判断是否是无效回答
     */
    private boolean isInvalidAnswer(String answer) {
        if (answer == null || answer.isEmpty()) {
            return true;
        }

        String[] invalidPatterns = {
                "不知道", "不清楚", "不会", "忘了", "记不清",
                "没学过", "不了解", "不明白", "不懂", "没听过",
                "没准备", "想不起来", "说不清"
        };

        String lowerAnswer = answer.toLowerCase();
        for (String pattern : invalidPatterns) {
            if (lowerAnswer.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 智能追问 - 针对不同情况
     */
    private String getSmartFollowUp(PositionType position, InterviewRound round, String context) {
        Map<String, List<String>> followUps = new HashMap<>();

        followUps.put("java", Arrays.asList(
                "能说说你在实际项目中用过哪些Java特性吗？",
                "那换个角度，你平时开发中遇到最多的异常是什么？",
                "没关系，我们聊点别的，你熟悉的Java框架有哪些？"
        ));

        followUps.put("python", Arrays.asList(
                "你在Python开发中常用的库有哪些？",
                "那聊聊你最近用Python做的项目吧",
                "换个问题，你对Python的异步编程有了解吗？"
        ));

        followUps.put("web", Arrays.asList(
                "你常用的前端框架是什么？",
                "聊聊你最近遇到过的浏览器兼容性问题",
                "那说说你熟悉的CSS布局方式"
        ));

        String key = "";
        if (position != null && position.getCode() != null) {
            key = position.getCode().toLowerCase();
        }

        List<String> questions = followUps.getOrDefault(key,
                Arrays.asList("那我们换个话题，你熟悉的技术栈是什么？"));

        Random random = new Random();
        return questions.get(random.nextInt(questions.size()));
    }

    /**
     * 鼓励性问题 - 当用户说不知道时
     */
    private String getEncouragingQuestion(PositionType position) {
        Map<String, String> encouragingMap = new HashMap<>();
        encouragingMap.put("java", "没关系，很多概念需要慢慢理解。那你能说说Java的基本数据类型有哪些吗？");
        encouragingMap.put("python", "不要紧张，我们聊点基础的。Python中的列表和元组有什么区别？");
        encouragingMap.put("web", "没问题，那我们从基础的开始，什么是盒模型？");

        String key = "";
        if (position != null && position.getCode() != null) {
            key = position.getCode().toLowerCase();
        }

        String result = encouragingMap.get(key);
        return result != null ? result : "没关系，我们换个简单点的问题...";
    }

    /**
     * 引导性问题 - 当回答太短时
     */
    private String getFollowUpQuestion(PositionType position) {
        Map<String, String> followUpMap = new HashMap<>();
        followUpMap.put("java", "能稍微详细点说说吗？比如你在实际项目中是怎么用的？");
        followUpMap.put("python", "可以举个例子说明吗？这样更容易理解");
        followUpMap.put("web", "能结合你遇到过的实际场景展开说说吗？");

        String key = "";
        if (position != null && position.getCode() != null) {
            key = position.getCode().toLowerCase();
        }

        String result = followUpMap.get(key);
        return result != null ? result : "能说得更具体一些吗？";
    }

    private String getRandomQuery(PositionType position) {
        String[] javaQueries = {"面向对象", "多线程", "集合", "异常"};
        String[] pythonQueries = {"列表推导式", "装饰器", "生成器"};
        String[] webQueries = {"闭包", "原型链", "事件循环"};
        String[] queries = switch (position) {
            case JAVA -> javaQueries;
            case PYTHON -> pythonQueries;
            case WEB -> webQueries;
            default -> javaQueries;
        };
        return queries[new Random().nextInt(queries.length)];
    }

    private String getFallbackQuestion(PositionType position, InterviewRound round) {
        Map<String, String> fallbacks = new HashMap<>();
        fallbacks.put("java_first", "请介绍一下Java的基本数据类型有哪些？");
        fallbacks.put("java_second", "谈谈你对面向对象编程三大特性的理解");
        fallbacks.put("java_third", "说一下JVM内存区域划分");
        fallbacks.put("python_first", "Python中的列表和元组有什么区别？");
        fallbacks.put("python_second", "解释一下Python的装饰器及其应用场景");
        fallbacks.put("web_first", "什么是事件冒泡和事件捕获？");
        fallbacks.put("web_second", "谈谈你对闭包的理解");

        String positionCode = "";
        String roundCode = "";

        if (position != null && position.getCode() != null) {
            positionCode = position.getCode().toLowerCase();
        }
        if (round != null) {
            roundCode = String.valueOf(round.getCode());
        }

        String key = positionCode + "_" + roundCode;
        String result = fallbacks.get(key);
        return result != null ? result : "请介绍一下你最擅长的技术点";
    }

    private EvaluationDTO parseEvaluation(String json) {
        try {
            if (json == null) {
                return getDefaultEvaluation();
            }

            if (json.contains("```")) {
                json = json.replaceAll("(?s)```json\\s*|```", "").trim();
            }
            JsonNode root = objectMapper.readTree(json);

            EvaluationDTO dto = new EvaluationDTO();
            dto.setScore(root.path("score").asInt(70));
            dto.setFeedback(root.path("feedback").asText("回答正确"));
            dto.setStrengths(parseJsonArray(root.path("strengths"), List.of()));
            dto.setWeaknesses(parseJsonArray(root.path("weaknesses"), List.of()));
            dto.setSuggestions(parseJsonArray(root.path("suggestions"), List.of()));

            return dto;
        } catch (Exception e) {
            log.error("解析评估结果失败: {}", e.getMessage());
            return getDefaultEvaluation();
        }
    }

    private EvaluationDTO getDefaultEvaluation() {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setScore(70);
        dto.setFeedback("回答基本正确");
        dto.setStrengths(List.of());
        dto.setWeaknesses(List.of());
        dto.setSuggestions(List.of());
        return dto;
    }

    private InterviewSummaryDTO parseSummary(String json) {
        try {
            if (json == null) {
                return getDefaultSummary();
            }

            if (json.contains("```")) {
                json = json.replaceAll("(?s)```json\\s*|```", "").trim();
            }
            JsonNode root = objectMapper.readTree(json);
            InterviewSummaryDTO dto = new InterviewSummaryDTO();
            dto.setTotalScore(root.path("totalScore").asInt(70));
            dto.setStrengths(parseJsonArray(root.path("strengths"), List.of("基础较好")));
            dto.setWeaknesses(parseJsonArray(root.path("weaknesses"), List.of("需要加强")));
            dto.setSuggestions(parseJsonArray(root.path("suggestions"), List.of("继续努力")));
            return dto;
        } catch (Exception e) {
            return getDefaultSummary();
        }
    }

    private List<String> parseJsonArray(JsonNode node, List<String> defaultValue) {
        if (node != null && node.isArray()) {
            List<String> list = new ArrayList<>();
            node.forEach(item -> list.add(item.asText()));
            return list.isEmpty() ? defaultValue : list;
        }
        return defaultValue;
    }

    private InterviewSummaryDTO getDefaultSummary() {
        InterviewSummaryDTO dto = new InterviewSummaryDTO();
        dto.setTotalScore(70);
        dto.setStrengths(List.of("基础较好"));
        dto.setWeaknesses(List.of("需要加强"));
        dto.setSuggestions(List.of("继续努力"));
        return dto;
    }

    // ==================== 新增方法：批量评估和完整报告 ====================

    /**
     * 批量评估所有答案（一次性调用AI）
     * @param session 面试会话
     * @return 评估结果列表
     */
    public List<EvaluationDTO> batchEvaluate(InterviewSessionDTO session) {
        long start = System.currentTimeMillis();
        try {
            // 1. 构建完整对话历史
            StringBuilder historyBuilder = new StringBuilder();
            if (session.getHistory() != null && !session.getHistory().isEmpty()) {
                for (int i = 0; i < session.getHistory().size(); i++) {
                    InterviewSessionDTO.QAPair pair = session.getHistory().get(i);
                    historyBuilder.append(String.format("【第%d题】\n", i + 1));
                    historyBuilder.append(String.format("问题：%s\n", pair.getQuestion()));
                    historyBuilder.append(String.format("回答：%s\n", pair.getUserAnswer()));
                    historyBuilder.append("\n");
                }
            } else {
                log.warn("没有历史记录，无法批量评估");
                return new ArrayList<>();
            }

            // 2. 构建提示词
            String prompt = String.format("""
                你是一位专业的%s岗位面试官，正在进行%s面试。
                
                请评估以下所有回答，为每个问题打分并给出详细评价：
                
                %s
                
                请以JSON数组格式返回，每个元素包含：
                {
                    "score": 0-100的整数,
                    "feedback": "一句话简要评价（20字以内）",
                    "strengths": ["优点1", "优点2"],
                    "weaknesses": ["不足1", "不足2"],
                    "suggestions": ["建议1", "建议2"]
                }
                
                评分标准：
                - 90-100：回答非常出色，有深度，逻辑清晰
                - 80-89：回答良好，有见解，基本正确
                - 70-79：回答一般，部分正确，有提升空间
                - 60-69：回答勉强及格，有明显不足
                - 60以下：回答错误或不会
                
                要求：
                1. 严格按顺序返回每个问题的评估
                2. 考虑回答的连贯性和一致性
                3. 只返回JSON数组，不要其他文字
                4. 每个评估都要具体、有针对性
                """,
                    session.getPosition(),
                    session.getRound(),
                    historyBuilder.toString()
            );

            // 3. 调用AI
            ChatClient chatClient = chatClientBuilder.build();
            String evaluationJson = chatClient.prompt(prompt).call().content();

            // 4. 清理JSON
            if (evaluationJson != null && evaluationJson.contains("```")) {
                evaluationJson = evaluationJson.replaceAll("(?s)```json\\s*|```", "").trim();
            }

            // 5. 解析为列表
            List<EvaluationDTO> evaluations = parseBatchEvaluation(evaluationJson);

            log.info("批量评估完成，共{}个回答，耗时: {}ms", evaluations.size(), System.currentTimeMillis() - start);
            return evaluations;

        } catch (Exception e) {
            log.error("批量评估失败", e);
            // 返回默认评估
            List<EvaluationDTO> defaultList = new ArrayList<>();
            if (session.getHistory() != null) {
                for (int i = 0; i < session.getHistory().size(); i++) {
                    defaultList.add(getDefaultEvaluation());
                }
            }
            return defaultList;
        }
    }

    /**
     * 解析批量评估结果
     */
    private List<EvaluationDTO> parseBatchEvaluation(String json) {
        List<EvaluationDTO> result = new ArrayList<>();
        try {
            if (json == null || json.isEmpty()) {
                return result;
            }

            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    EvaluationDTO dto = new EvaluationDTO();
                    dto.setScore(node.path("score").asInt(70));
                    dto.setFeedback(node.path("feedback").asText("回答基本正确"));
                    dto.setStrengths(parseJsonArray(node.path("strengths"), List.of()));
                    dto.setWeaknesses(parseJsonArray(node.path("weaknesses"), List.of()));
                    dto.setSuggestions(parseJsonArray(node.path("suggestions"), List.of()));
                    result.add(dto);
                }
            } else {
                // 如果不是数组，尝试作为单个对象解析
                EvaluationDTO dto = parseEvaluation(json);
                result.add(dto);
            }
        } catch (Exception e) {
            log.error("解析批量评估结果失败", e);
        }
        return result;
    }

    /**
     * 生成完整面试报告
     * @param session 面试会话
     * @param evaluations 评估结果列表
     * @param avgScore 平均分
     * @return 完整报告
     */
    public com.ningcai.interview.model.dto.InterviewReportDTO generateFullReport(
            InterviewSessionDTO session,
            List<EvaluationDTO> evaluations,
            int avgScore) {

        com.ningcai.interview.model.dto.InterviewReportDTO report =
                new com.ningcai.interview.model.dto.InterviewReportDTO();

        // 基本信息
        report.setSessionId(session.getSessionId());
        report.setPosition(session.getPosition());
        report.setRound(session.getRound());
        report.setTotalQuestions(session.getHistory() != null ? session.getHistory().size() : 0);
        report.setTotalScore(avgScore);

        // 设置每道题的详细评估
        List<com.ningcai.interview.model.dto.InterviewReportDTO.QuestionDetail> details = new ArrayList<>();
        if (session.getHistory() != null) {
            for (int i = 0; i < session.getHistory().size(); i++) {
                InterviewSessionDTO.QAPair pair = session.getHistory().get(i);
                EvaluationDTO eval = i < evaluations.size() ? evaluations.get(i) : getDefaultEvaluation();

                com.ningcai.interview.model.dto.InterviewReportDTO.QuestionDetail detail =
                        new com.ningcai.interview.model.dto.InterviewReportDTO.QuestionDetail();
                detail.setQuestionNumber(i + 1);
                detail.setQuestion(pair.getQuestion());
                detail.setUserAnswer(pair.getUserAnswer());
                detail.setScore(eval.getScore());
                detail.setFeedback(eval.getFeedback());
                detail.setStrengths(eval.getStrengths() != null ? eval.getStrengths() : List.of());
                detail.setWeaknesses(eval.getWeaknesses() != null ? eval.getWeaknesses() : List.of());
                detail.setSuggestions(eval.getSuggestions() != null ? eval.getSuggestions() : List.of());
                details.add(detail);
            }
        }
        report.setQuestionDetails(details);

        // 生成总结性报告
        String summary = generateComprehensiveSummary(session, evaluations, avgScore);
        report.setSummary(summary);

        // 生成整体改进建议
        List<String> overallSuggestions = generateOverallSuggestions(evaluations);
        report.setOverallSuggestions(overallSuggestions);

        // 生成优势总结
        List<String> overallStrengths = generateOverallStrengths(evaluations);
        report.setOverallStrengths(overallStrengths);

        // 生成不足总结
        List<String> overallWeaknesses = generateOverallWeaknesses(evaluations);
        report.setOverallWeaknesses(overallWeaknesses);

        return report;
    }

    /**
     * 生成综合总结
     */
    private String generateComprehensiveSummary(
            InterviewSessionDTO session,
            List<EvaluationDTO> evaluations,
            int avgScore) {

        StringBuilder summary = new StringBuilder();

        // 总体评价
        if (avgScore >= 85) {
            summary.append("【总体评价】表现优秀！");
        } else if (avgScore >= 70) {
            summary.append("【总体评价】表现良好，有提升空间。");
        } else {
            summary.append("【总体评价】表现一般，需要加强学习。");
        }

        // 根据得分给出建议
        if (avgScore >= 85) {
            summary.append("技术基础扎实，表达清晰，建议进入下一轮面试。");
        } else if (avgScore >= 70) {
            summary.append("基础知识掌握较好，但深度有待加强，建议复习后再进行下一轮。");
        } else {
            summary.append("基础知识不牢固，建议系统学习后再参加面试。");
        }

        // 添加具体建议
        summary.append("\n\n【学习建议】");
        List<String> suggestions = generateOverallSuggestions(evaluations);
        for (int i = 0; i < Math.min(suggestions.size(), 3); i++) {
            summary.append("\n").append(i + 1).append(". ").append(suggestions.get(i));
        }

        return summary.toString();
    }

    /**
     * 生成整体优势总结
     */
    private List<String> generateOverallStrengths(List<EvaluationDTO> evaluations) {
        Map<String, Integer> strengthCount = new HashMap<>();
        for (EvaluationDTO eval : evaluations) {
            if (eval.getStrengths() != null) {
                for (String strength : eval.getStrengths()) {
                    strengthCount.put(strength, strengthCount.getOrDefault(strength, 0) + 1);
                }
            }
        }

        // 提取出现次数最多的3个优势
        List<String> result = strengthCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return result.isEmpty() ? List.of("基础概念清晰", "态度积极") : result;
    }

    /**
     * 生成整体不足总结
     */
    private List<String> generateOverallWeaknesses(List<EvaluationDTO> evaluations) {
        Map<String, Integer> weaknessCount = new HashMap<>();
        for (EvaluationDTO eval : evaluations) {
            if (eval.getWeaknesses() != null) {
                for (String weakness : eval.getWeaknesses()) {
                    weaknessCount.put(weakness, weaknessCount.getOrDefault(weakness, 0) + 1);
                }
            }
        }

        // 提取出现次数最多的3个不足
        List<String> result = weaknessCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return result.isEmpty() ? List.of("需要加强实践", "深度有待提升") : result;
    }

    /**
     * 生成整体改进建议
     */
    private List<String> generateOverallSuggestions(List<EvaluationDTO> evaluations) {
        Map<String, Integer> suggestionCount = new HashMap<>();
        for (EvaluationDTO eval : evaluations) {
            if (eval.getSuggestions() != null) {
                for (String suggestion : eval.getSuggestions()) {
                    suggestionCount.put(suggestion, suggestionCount.getOrDefault(suggestion, 0) + 1);
                }
            }
        }

        // 提取出现次数最多的3个建议
        List<String> suggestions = suggestionCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (suggestions.isEmpty()) {
            return List.of("继续努力学习", "多实践项目", "复习基础知识");
        }
        return suggestions;
    }

    /**
     * 异步生成详细分析
     */
    @Async
    public CompletableFuture<String> generateDetailedAnalysis(
            InterviewSessionDTO session,
            List<EvaluationDTO> evaluations) {

        long start = System.currentTimeMillis();
        try {
            StringBuilder analysisBuilder = new StringBuilder();
            analysisBuilder.append("【详细面试分析报告】\n\n");

            if (session.getHistory() != null) {
                for (int i = 0; i < session.getHistory().size(); i++) {
                    InterviewSessionDTO.QAPair pair = session.getHistory().get(i);
                    EvaluationDTO eval = i < evaluations.size() ? evaluations.get(i) : getDefaultEvaluation();

                    analysisBuilder.append(String.format("第%d题：%s\n", i + 1, pair.getQuestion()));
                    analysisBuilder.append(String.format("得分：%d分\n", eval.getScore()));
                    analysisBuilder.append(String.format("评价：%s\n", eval.getFeedback()));
                    analysisBuilder.append("优势：").append(String.join("、", eval.getStrengths())).append("\n");
                    analysisBuilder.append("不足：").append(String.join("、", eval.getWeaknesses())).append("\n");
                    analysisBuilder.append("建议：").append(String.join("、", eval.getSuggestions())).append("\n");
                    analysisBuilder.append("\n");
                }
            }

            log.info("详细分析生成完成，耗时: {}ms", System.currentTimeMillis() - start);
            return CompletableFuture.completedFuture(analysisBuilder.toString());

        } catch (Exception e) {
            log.error("生成详细分析失败", e);
            return CompletableFuture.completedFuture("生成分析失败");
        }
    }
}