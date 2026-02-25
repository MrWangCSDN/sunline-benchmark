import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fastjson BigDecimal 序列化测试（简化版）
 * 此测试模拟 Fastjson 的行为，展示科学计数法问题
 * 
 * 要运行完整的 Fastjson 测试，需要：
 * 1. 在 pom.xml 中添加 Fastjson 依赖
 * 2. 运行 FastjsonBigDecimalTest.java
 */
public class FastjsonBigDecimalSimpleTest {
    
    public static void main(String[] args) {
        System.out.println("================== Fastjson BigDecimal 序列化问题模拟 ==================");
        System.out.println();
        
        // 模拟 Fastjson 的序列化行为
        System.out.println("【问题演示】BigDecimal 值为 0，scale = 7");
        BigDecimal bd = BigDecimal.ZERO.setScale(7, RoundingMode.HALF_UP);
        System.out.println("  BigDecimal 值: " + bd);
        System.out.println("  scale: " + bd.scale());
        System.out.println();
        
        System.out.println("【Fastjson 默认序列化行为】");
        System.out.println("  Fastjson 会调用 BigDecimal.toString()");
        System.out.println("  toString() 结果: " + bd.toString() + "  ❌ 科学计数法");
        System.out.println();
        
        System.out.println("【Fastjson 序列化后的 JSON】");
        String simulatedJson = "{\"amount\":" + bd.toString() + "}";
        System.out.println("  " + simulatedJson);
        System.out.println("  问题：前端接收到的是科学计数法格式！");
        System.out.println();
        
        System.out.println("【解决方案1】使用 WriteBigDecimalAsString 特性");
        System.out.println("  Fastjson 1.x: JSON.toJSONString(entity, SerializerFeature.WriteBigDecimalAsString)");
        System.out.println("  Fastjson 2.x: JSON.toJSONString(entity, JSONWriter.Feature.WriteBigDecimalAsString)");
        System.out.println("  结果: {\"amount\":\"" + bd.toPlainString() + "\"}  ✅ 字符串格式");
        System.out.println();
        
        System.out.println("【解决方案2】业务层处理");
        System.out.println("  使用 toPlainString(): " + bd.toPlainString() + "  ✅");
        System.out.println("  或判断为0返回\"0\": " + (bd.compareTo(BigDecimal.ZERO) == 0 ? "0" : bd.toPlainString()) + "  ✅");
        System.out.println();
        
        // 测试不同 scale 值
        System.out.println("【不同 scale 值的影响】");
        System.out.println("  scale < 7: toString() 正常显示");
        System.out.println("  scale >= 7: toString() 使用科学计数法");
        System.out.println();
        for (int scale = 0; scale <= 10; scale++) {
            BigDecimal testBd = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
            String toString = testBd.toString();
            boolean isScientific = toString.contains("E") || toString.contains("e");
            System.out.printf("  scale=%2d: toString()=%15s  %s\n", 
                scale, toString, isScientific ? "❌ 科学计数法" : "✅ 正常");
        }
        System.out.println();
        
        // 反序列化测试
        System.out.println("【反序列化测试】");
        System.out.println("  如果 JSON 中包含科学计数法格式：");
        String jsonWithScientific = "{\"amount\":\"0E-7\"}";
        System.out.println("  " + jsonWithScientific);
        System.out.println("  Fastjson 可以正确反序列化，但得到的 BigDecimal 对象：");
        System.out.println("    toString(): " + bd.toString() + "  ❌ 仍显示科学计数法");
        System.out.println("    toPlainString(): " + bd.toPlainString() + "  ✅ 正常格式");
        System.out.println();
        
        System.out.println("================== 结论 ==================");
        System.out.println("1. Fastjson 默认序列化 BigDecimal 时，会调用 toString() 方法");
        System.out.println("2. 当 scale >= 7 且值为 0 时，toString() 返回科学计数法（0E-7）");
        System.out.println("3. 解决方案：");
        System.out.println("   - 使用 WriteBigDecimalAsString 特性（推荐）");
        System.out.println("   - 或业务层统一使用 toPlainString() 处理");
        System.out.println("4. 反序列化后，建议使用 toPlainString() 而不是 toString()");
    }
}

