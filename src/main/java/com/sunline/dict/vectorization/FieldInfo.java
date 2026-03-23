package com.sunline.dict.vectorization;

/**
 * 统一字段信息（用于 ServiceDetail 和 ComponentDetail 的向量化文本构建）
 */
public class FieldInfo {
    public final String inputLongname;
    public final String inputType;
    public final String inputMulti;
    public final String outputLongname;
    public final String outputType;
    public final String outputMulti;

    public FieldInfo(String inputLongname, String inputType, String inputMulti,
                     String outputLongname, String outputType, String outputMulti) {
        this.inputLongname  = inputLongname;
        this.inputType      = inputType;
        this.inputMulti     = inputMulti;
        this.outputLongname = outputLongname;
        this.outputType     = outputType;
        this.outputMulti    = outputMulti;
    }
}
