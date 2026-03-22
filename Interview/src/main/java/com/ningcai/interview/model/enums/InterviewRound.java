package com.ningcai.interview.model.enums;

public enum InterviewRound {
    FIRST(1, "一面"),
    SECOND(2, "二面"),
    THIRD(3, "三面"),
    FOURTH(4, "四面"),
    GENERAL(0, "通用题库");  // 总库
    
    private int code;
    private String desc;
    
    InterviewRound(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    public int getCode() { return code; }
    public String getDesc() { return desc; }
    
    // 根据路径判断轮次
    public static InterviewRound fromPath(String path) {
        if (path.contains("一面")) return FIRST;
        if (path.contains("二面")) return SECOND;
        if (path.contains("三面")) return THIRD;
        if (path.contains("四面")) return FOURTH;
        return GENERAL;  // 题库类都归为通用
    }
}