package com.ningcai.interview.model.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class InterviewSessionDTO {
    private String sessionId;
    private String position;
    private String round;
    private Long userId;
    private String welcome;
    private String firstQuestion;
    private String currentQuestion;  // 当前问题
    private int totalQuestions;      // 总题目数
    private String estimatedTime;
    private List<QAPair> history;    // 对话历史

    @Data
    public static class QAPair {
        private String question;      // 问题
        private String userAnswer;    // 用户答案
        private EvaluationDTO evaluation;  // 评估结果（最后填充）
    }

    public InterviewSessionDTO() {
        this.sessionId = UUID.randomUUID().toString();
        this.totalQuestions = 5;      // ✅ 改为5个问题（原先是10个，太多）
        this.estimatedTime = "15分钟";
        this.welcome = "你好，我是你的AI面试官。今天我们来一场模拟面试，放轻松，就当是真实的面试场景。";
        this.history = new ArrayList<>();  // ✅ 初始化空列表
    }

    // ✅ 添加一个辅助方法，方便获取已回答的问题数
    public int getAnsweredCount() {
        return history != null ? history.size() : 0;
    }

    // ✅ 添加一个辅助方法，判断是否完成所有问题
    public boolean isCompleted() {
        return getAnsweredCount() >= totalQuestions;
    }
}