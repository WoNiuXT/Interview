package com.ningcai.interview.model.dto;

import lombok.Data;
import java.util.List;

/**
 * 面试完整报告DTO
 * 用于 /finish 接口返回
 */
@Data
public class InterviewReportDTO {
    
    // ========== 基本信息 ==========
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 岗位
     */
    private String position;
    
    /**
     * 面试轮次
     */
    private String round;
    
    /**
     * 总题数
     */
    private Integer totalQuestions;
    
    /**
     * 总分（平均分）
     */
    private Integer totalScore;
    
    /**
     * 下一轮建议
     */
    private String nextRound;
    
    // ========== 详细评估 ==========
    /**
     * 每道题的详细评估
     */
    private List<QuestionDetail> questionDetails;
    
    // ========== 总结 ==========
    /**
     * 综合总结（一段文字）
     */
    private String summary;
    
    /**
     * 整体优势（2-3个）
     */
    private List<String> overallStrengths;
    
    /**
     * 整体不足（2-3个）
     */
    private List<String> overallWeaknesses;
    
    /**
     * 整体改进建议（2-3个）
     */
    private List<String> overallSuggestions;
    
    /**
     * 问题详情内部类
     */
    @Data
    public static class QuestionDetail {
        /**
         * 问题序号（第几题）
         */
        private Integer questionNumber;
        
        /**
         * 问题内容
         */
        private String question;
        
        /**
         * 用户答案
         */
        private String userAnswer;
        
        /**
         * 得分（0-100）
         */
        private Integer score;
        
        /**
         * 简要评价
         */
        private String feedback;
        
        /**
         * 具体优势
         */
        private List<String> strengths;
        
        /**
         * 具体不足
         */
        private List<String> weaknesses;
        
        /**
         * 改进建议
         */
        private List<String> suggestions;
    }
}