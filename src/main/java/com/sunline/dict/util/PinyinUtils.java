package com.sunline.dict.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 拼音转换工具
 */
public class PinyinUtils {

    private static final Logger log = LoggerFactory.getLogger(PinyinUtils.class);

    private static final HanyuPinyinOutputFormat FORMAT;

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setCaseType(HanyuPinyinCaseType.UPPERCASE);
    }

    private PinyinUtils() {
    }

    /**
     * 将中文转换为全大写、下划线分隔的拼音。
     *
     * @param source 中文字符串
     * @return 转换后的拼音（示例：文本1000 -> WEN_BEN_1000）
     */
    public static String toUpperCaseWithUnderscore(String source) {
        if (source == null || source.trim().isEmpty()) {
            return "";
        }

        String normalized = source.trim();
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (isChinese(ch)) {
                flushToken(tokens, current);
                tokens.add(convertChineseChar(ch));
            } else if (Character.isLetterOrDigit(ch)) {
                current.append(Character.toUpperCase(ch));
            } else if (ch == '.' || ch == '·') {
                flushToken(tokens, current);
            } else if (!Character.isWhitespace(ch)) {
                flushToken(tokens, current);
            } else {
                flushToken(tokens, current);
            }
        }

        flushToken(tokens, current);

        String joinString = String.join("_", tokens);
        String result = joinString.replace("LU:", "LV");

        return result;
    }

    private static void flushToken(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean isChinese(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }

    private static String convertChineseChar(char ch) {
        try {
            String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
            if (pinyins != null && pinyins.length > 0) {
                return pinyins[0];
            }
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            log.warn("汉字转拼音失败: {}", ch, e);
        }
        return String.valueOf(ch).toUpperCase(Locale.ROOT);
    }
}


