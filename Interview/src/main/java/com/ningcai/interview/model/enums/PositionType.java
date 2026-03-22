package com.ningcai.interview.model.enums;

public enum PositionType {
    JAVA("java", "Java后端"),
    PYTHON("python", "Python工程师"),
    WEB("web", "Web前端"),
    TEST("test", "测试工程师"),
    OPS("ops", "运维工程师");
    
    private String code;
    private String desc;
    
    PositionType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    public String getCode() { return code; }
    public String getDesc() { return desc; }
    
    // 根据路径判断岗位
    public static PositionType fromPath(String path) {
        if (path.contains("java")) return JAVA;
        if (path.contains("python")) return PYTHON;
        if (path.contains("Web前端")) return WEB;
        if (path.contains("测试")) return TEST;
        if (path.contains("运维")) return OPS;
        return null;
    }
}