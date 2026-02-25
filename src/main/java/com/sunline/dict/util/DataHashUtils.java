package com.sunline.dict.util;

import com.sunline.dict.entity.DictData;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 数据Hash计算工具类
 */
public class DataHashUtils {
    
    /**
     * 计算数据行的MD5值
     * 将所有业务字段拼接后计算MD5，用于快速判断数据是否变化
     */
    public static String calculateHash(DictData data) {
        if (data == null) {
            return "";
        }
        
        // 将所有业务字段拼接（不包括id、版本等元数据字段）
        String content = String.join("|",
            nullSafe(data.getDataItemCode()),
            nullSafe(data.getEnglishAbbr()),
            nullSafe(data.getChineseName()),
            nullSafe(data.getDictAttr()),
            nullSafe(data.getDomainChineseName()),
            nullSafe(data.getDataType()),
            nullSafe(data.getDataFormat()),
            nullSafe(data.getJavaEsfName()),
            nullSafe(data.getEsfDataFormat()),
            nullSafe(data.getGaussdbDataFormat()),
            nullSafe(data.getGoldendbDataFormat())
        );
        
        // 计算MD5
        return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 空值安全处理
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}

