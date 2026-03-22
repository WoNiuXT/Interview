package com.ningcai.interview.controller;

import com.ningcai.interview.model.dto.*;
import com.ningcai.interview.model.enums.InterviewRound;
import com.ningcai.interview.model.enums.PositionType;
import com.ningcai.interview.service.InterviewService;
import com.ningcai.interview.service.MaterialLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final MaterialLoadService materialLoadService;
    private final Map<String, InterviewSessionDTO> sessionMap = new ConcurrentHashMap<>();

    // ==================== 核心面试接口（3个） ====================

    /**
     * 1. 开始面试
     */
    @PostMapping("/start")
    public ResponseEntity<?> startInterview(@RequestBody StartInterviewRequest request) {
        long start = System.currentTimeMillis();
        try {
            // 参数校验
            if (request.getPosition() == null || request.getPosition().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("岗位不能为空");
            }
            if (request.getRound() == null || request.getRound().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("面试轮次不能为空");
            }

            // 创建会话
            InterviewSessionDTO session = new InterviewSessionDTO();
            session.setPosition(request.getPosition());
            session.setRound(request.getRound());
            session.setUserId(request.getUserId());

            // 获取第一个问题（带缓存）
            String firstQuestion = interviewService.generateQuestion(
                    PositionType.valueOf(request.getPosition().toUpperCase()),
                    parseRound(request.getRound())
            );
            session.setFirstQuestion(firstQuestion);
            session.setCurrentQuestion(firstQuestion);

            // 初始化历史记录列表
            if (session.getHistory() == null) {
                session.setHistory(new ArrayList<>());
            }

            sessionMap.put(session.getSessionId(), session);
            log.info("开始面试 - sessionId: {}, 岗位: {}, 轮次: {}, 第一个问题: {}, 耗时: {}ms",
                    session.getSessionId(),      // 第1个 {}
                    request.getPosition(),       // 第2个 {}
                    request.getRound(),          // 第3个 {}
                    firstQuestion,               // 第4个 {} ← 这里是第一个问题
                    System.currentTimeMillis() - start);  // 第5个 {} ← 这里是耗时
            return ResponseEntity.ok(session);

        } catch (Exception e) {
            log.error("开始面试失败", e);
            return ResponseEntity.status(500).body("开始面试失败：" + e.getMessage());
        }

    }

    /**
     * 2. 回答问题并获取下一个问题（核心接口）
     */
    @PostMapping("/next/{sessionId}")
    public ResponseEntity<?> answerAndGetNext(
            @PathVariable String sessionId,
            @RequestBody AnswerRequest request) {
        long start = System.currentTimeMillis();
        try {
            // 1. 获取会话
            InterviewSessionDTO session = sessionMap.get(sessionId);
            if (session == null) {
                return ResponseEntity.status(404).body("面试会话不存在");
            }

            // 2. 参数校验
            if (request.getAnswer() == null || request.getAnswer().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("答案不能为空");
            }

            // 3. 保存当前答案到历史（只记录，不评估）
            InterviewSessionDTO.QAPair pair = new InterviewSessionDTO.QAPair();
            pair.setQuestion(session.getCurrentQuestion());
            pair.setUserAnswer(request.getAnswer());
            // 注意：这里不设置 evaluation，留到最后统一评估
            session.getHistory().add(pair);

            int answeredCount = session.getHistory().size();
            log.info("保存答案 - sessionId: {}, 当前进度: {}/{}",
                    sessionId, answeredCount, session.getTotalQuestions());

            // 4. 检查是否达到题目数量
            if (answeredCount >= session.getTotalQuestions()) {
                log.info("已达到预设题目数量，返回完成标记");
                NextQuestionResponse response = new NextQuestionResponse();
                response.setStatus("completed");
                response.setMessage("已完成所有题目，请调用结束接口");
                response.setProgress(answeredCount + "/" + session.getTotalQuestions());
                return ResponseEntity.ok(response);
            }

            // 5. 生成下一题（基于完整历史）
            String historyText = buildFullHistory(session);
            String nextQuestion = interviewService.generateNextQuestion(
                    PositionType.valueOf(session.getPosition().toUpperCase()),
                    parseRound(session.getRound()),
                    request.getAnswer(),
                    historyText
            );

            // 6. 更新当前问题
            session.setCurrentQuestion(nextQuestion);

            // 7. 返回下一题
            NextQuestionResponse response = new NextQuestionResponse();
            response.setQuestionId("q_" + System.currentTimeMillis());
            response.setQuestion(nextQuestion);
            response.setProgress(answeredCount + "/" + session.getTotalQuestions());
            response.setStatus("ongoing");

            log.info("获取下一个问题成功 - sessionId: {}, 耗时: {}ms", sessionId, System.currentTimeMillis() - start);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取下一个问题失败 - sessionId: {}", sessionId, e);
            return ResponseEntity.status(500).body("获取下一个问题失败：" + e.getMessage());
        }
    }

    /**
     * 3. 结束面试 - 生成完整报告
     */
    @PostMapping("/finish/{sessionId}")
    public ResponseEntity<?> finishInterview(
            @PathVariable String sessionId,
            @RequestBody(required = false) FinishInterviewRequest request) {
        long start = System.currentTimeMillis();
        try {
            // 1. 获取会话（从map中移除，表示面试结束）
            InterviewSessionDTO session = sessionMap.remove(sessionId);
            if (session == null) {
                return ResponseEntity.status(404).body("面试会话不存在或已结束");
            }

            int answeredCount = session.getHistory() != null ? session.getHistory().size() : 0;
            int totalQuestions = session.getTotalQuestions();

            log.info("结束面试 - sessionId: {}, 已答题数: {}/{}",
                    sessionId, answeredCount, totalQuestions);

            // 2. 处理未完成的情况：如果还没答完所有题，但有最后一题答案
            if (answeredCount < totalQuestions && request != null
                    && request.getLastAnswer() != null && !request.getLastAnswer().isEmpty()) {
                log.info("检测到提前结束面试，保存最后一题答案");

                String lastQuestion = request.getLastQuestion();
                if (lastQuestion == null || lastQuestion.isEmpty()) {
                    lastQuestion = session.getCurrentQuestion();
                }

                InterviewSessionDTO.QAPair lastPair = new InterviewSessionDTO.QAPair();
                lastPair.setQuestion(lastQuestion);
                lastPair.setUserAnswer(request.getLastAnswer());
                session.getHistory().add(lastPair);
                answeredCount++;

                log.info("已保存最后一题答案，现在总答题数: {}", answeredCount);
            } else if (answeredCount < totalQuestions) {
                // 既没答完题，也没传最后一题答案
                log.warn("面试提前结束，但未提供最后一题答案，已答题数: {}/{}", answeredCount, totalQuestions);
                return ResponseEntity.badRequest().body(String.format(
                        "面试未完成！已完成 %d/%d 题，请先完成所有题目或提供最后一题答案",
                        answeredCount, totalQuestions));
            }

            // 3. 验证是否有答题记录
            if (session.getHistory() == null || session.getHistory().isEmpty()) {
                log.warn("面试结束但没有任何答题记录");
                return ResponseEntity.badRequest().body("没有答题记录，无法生成报告");
            }

            // 4. 批量评估所有答案（一次性调用AI）
            log.info("开始批量评估所有答案，共 {} 个回答", session.getHistory().size());
            List<EvaluationDTO> evaluations = interviewService.batchEvaluate(session);

            // 5. 将评估结果关联到历史记录
            for (int i = 0; i < evaluations.size() && i < session.getHistory().size(); i++) {
                session.getHistory().get(i).setEvaluation(evaluations.get(i));
            }

            // 6. 计算总分和平均分
            int totalScore = evaluations.stream()
                    .mapToInt(e -> e != null ? e.getScore() : 0)
                    .sum();
            int avgScore = totalScore / evaluations.size();

            log.info("批量评估完成 - 总题数: {}, 总分: {}, 平均分: {}",
                    evaluations.size(), totalScore, avgScore);

            // 7. 生成完整面试报告
            InterviewReportDTO report = interviewService.generateFullReport(session, evaluations, avgScore);

            // 8. 根据平均分决定下一轮
            String nextRound = determineNextRound(session.getRound(), avgScore);
            report.setNextRound(nextRound);
            report.setTotalScore(avgScore);
            report.setTotalQuestions(session.getHistory().size());

            // 9. 异步生成详细分析
            CompletableFuture.runAsync(() -> {
                interviewService.generateDetailedAnalysis(session, evaluations)
                        .thenAccept(detailedAnalysis -> {
                            log.info("详细分析生成完成 - sessionId: {}", sessionId);
                        });
            });

            log.info("面试结束 - 总答题数: {}, 平均分: {}, 下一轮: {}, 耗时: {}ms",
                    session.getHistory().size(), avgScore, nextRound, System.currentTimeMillis() - start);

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("结束面试失败 - sessionId: {}", sessionId, e);
            return ResponseEntity.status(500).body("结束面试失败：" + e.getMessage());
        }
    }
    // ==================== 辅助接口（保留兼容） ====================

    /**
     * 获取岗位列表
     */
    @GetMapping("/positions")
    public ResponseEntity<?> getPositions() {
        List<Map<String, String>> positions = new ArrayList<>();
        for (PositionType position : PositionType.values()) {
            positions.add(Map.of(
                    "code", position.getCode(),
                    "name", position.getDesc()
            ));
        }
        return ResponseEntity.ok(positions);
    }

    /**
     * 获取轮次列表
     */
    @GetMapping("/rounds")
    public ResponseEntity<?> getRounds() {
        List<Map<String, String>> rounds = List.of(
                Map.of("code", "first", "name", "一面"),
                Map.of("code", "second", "name", "二面"),
                Map.of("code", "third", "name", "三面"),
                Map.of("code", "fourth", "name", "四面"),
                Map.of("code", "general", "name", "通用题库")
        );
        return ResponseEntity.ok(rounds);
    }

    /**
     * 资料管理接口（管理员用）
     */
    @RestController
    @RequestMapping("/api/interview/material")
    public class MaterialController {

        @PostMapping("/load/{position}")
        public String loadPositionMaterials(@PathVariable String position) throws Exception {
            PositionType positionType = PositionType.valueOf(position.toUpperCase());
            return materialLoadService.loadPositionMaterials(positionType);
        }

        @PostMapping("/load-all")
        public String loadAllMaterials() throws Exception {
            return materialLoadService.loadAllMaterials();
        }
    }

    // ==================== 内部类（请求/响应 DTO） ====================

    @lombok.Data
    public static class StartInterviewRequest {
        private String position;
        private String round;
        private Long userId;
    }

    @lombok.Data
    public static class AnswerRequest {
        private String answer;  // 当前问题的答案
        private String questionId;  // 可选：问题ID
    }

    @lombok.Data
    public static class NextQuestionResponse {
        private String questionId;
        private String question;
        private String progress;  // 进度，如 "3/5"
        private String status;    // ongoing, completed
        private String message;   // 可选提示信息
    }

    @lombok.Data
    public static class FinishInterviewRequest {
        private String lastQuestionId;    // 可选：最后一题的ID
        private String lastQuestion;      // 可选：最后一题的问题内容
        private String lastAnswer;        // 可选：用户最后一题的答案（提前结束时需要）
    }


    // ==================== 辅助方法 ====================

    /**
     * 构建完整的历史对话文本
     */
    private String buildFullHistory(InterviewSessionDTO session) {
        if (session.getHistory() == null || session.getHistory().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < session.getHistory().size(); i++) {
            InterviewSessionDTO.QAPair pair = session.getHistory().get(i);
            sb.append(String.format("第%d轮对话：\n", i + 1));
            sb.append("面试官：").append(pair.getQuestion()).append("\n");
            sb.append("求职者：").append(pair.getUserAnswer()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 根据当前轮次和得分决定下一轮
     */
    private String determineNextRound(String currentRound, int score) {
        // 不及格线
        if (score < 60) {
            return "failed";
        }

        return switch (currentRound.toLowerCase()) {
            case "first" -> score >= 85 ? "second" : "first_retry";
            case "second" -> score >= 85 ? "third" : "second_retry";
            case "third" -> score >= 80 ? "fourth" : "third_retry";
            case "fourth" -> score >= 75 ? "passed" : "fourth_retry";
            default -> "general";
        };
    }

    private InterviewRound parseRound(String round) {
        if (round == null) return InterviewRound.GENERAL;
        return switch (round.toLowerCase()) {
            case "first" -> InterviewRound.FIRST;
            case "second" -> InterviewRound.SECOND;
            case "third" -> InterviewRound.THIRD;
            case "fourth" -> InterviewRound.FOURTH;
            default -> InterviewRound.GENERAL;
        };
    }

    private String getCurrentQuestion(InterviewSessionDTO session) {
        if (session == null) return "未知问题";
        if (session.getHistory() != null && !session.getHistory().isEmpty()) {
            return session.getHistory().get(session.getHistory().size() - 1).getQuestion();
        }
        return session.getFirstQuestion() != null ? session.getFirstQuestion() : "请开始回答";
    }

    private String getCategoryByPosition(String position) {
        if (position == null) return "技术基础";
        return switch (position.toLowerCase()) {
            case "java" -> "Java基础";
            case "python" -> "Python基础";
            case "web" -> "前端基础";
            default -> "技术基础";
        };
    }

    private String getDifficultyByRound(String round) {
        if (round == null) return "中等";
        return switch (round.toLowerCase()) {
            case "first" -> "简单";
            case "second" -> "中等";
            case "third" -> "困难";
            case "fourth" -> "专家";
            default -> "中等";
        };
    }
}