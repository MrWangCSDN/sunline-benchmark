import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fastjson BigDecimal 序列化和反序列化测试
 * 测试版本：fastjson 1.2.83
 */
public class FastjsonBigDecimalTest {

    public static void main(String[] args) {
        System.out.println("================== Fastjson BigDecimal 序列化和反序列化测试 ==================");
        System.out.println("Fastjson 版本: 1.2.83");
        System.out.println();

        // 测试1：值为 0，scale = 7（14位整数 + 7位小数）- 默认方式
        System.out.println("【测试1】值为 0，scale = 7（14位整数 + 7位小数）- 默认方式");
        BigDecimal bd1 = new BigDecimal("0").setScale(7, RoundingMode.HALF_UP);
        testSerializeAndDeserialize("bd1", bd1, false);
        System.out.println();

        // 测试1-2：值为 0，scale = 7 - 使用 WriteBigDecimalAsPlain 方式
        System.out.println("【测试1-2】值为 0，scale = 7 - 使用 WriteBigDecimalAsPlain 方式");
        testSerializeAndDeserialize("bd1", bd1, true);
        System.out.println();

        // 测试2：正常值，14位整数 + 7位小数 - 使用 WriteBigDecimalAsPlain
        System.out.println("【测试2】正常值，14位整数 + 7位小数 - 使用 WriteBigDecimalAsPlain");
        BigDecimal bd2 = new BigDecimal("12345678901234.1234567");
        testSerializeAndDeserialize("bd2", bd2, true);
        System.out.println();

        // 测试3：很大的整数部分 - 使用 WriteBigDecimalAsPlain
        System.out.println("【测试3】很大的整数部分 - 使用 WriteBigDecimalAsPlain");
        BigDecimal bd3 = new BigDecimal("99999999999999.1234567");
        testSerializeAndDeserialize("bd3", bd3, true);
        System.out.println();

        // 测试4：很小的值 - 使用 WriteBigDecimalAsPlain
        System.out.println("【测试4】很小的值 - 使用 WriteBigDecimalAsPlain");
        BigDecimal bd4 = new BigDecimal("0.0000001");
        testSerializeAndDeserialize("bd4", bd4, true);
        System.out.println();

        // 测试5：整数（无小数部分）- 使用 WriteBigDecimalAsPlain
        System.out.println("【测试5】整数（无小数部分）- 使用 WriteBigDecimalAsPlain");
        BigDecimal bd5 = new BigDecimal("12345678901234");
        testSerializeAndDeserialize("bd5", bd5, true);
        System.out.println();

        // 测试6：负数 - 使用 WriteBigDecimalAsPlain
        System.out.println("【测试6】负数 - 使用 WriteBigDecimalAsPlain");
        BigDecimal bd6 = new BigDecimal("-12345678901234.1234567");
        testSerializeAndDeserialize("bd6", bd6, true);
        System.out.println();

        // 测试7：从字符串创建，scale = 7 - 使用 WriteBigDecimalAsPlain
        System.out.println("【测试7】从字符串创建，scale = 7 - 使用 WriteBigDecimalAsPlain");
        BigDecimal bd7 = new BigDecimal("0.0000000");
        testSerializeAndDeserialize("bd7", bd7, true);
        System.out.println();

        // 测试8：测试对象序列化 - 默认方式
        System.out.println("================== 对象序列化测试 - 默认方式 ==================");
        TestBean bean = new TestBean();
        bean.setId(1L);
        bean.setName("测试");
        bean.setAmount(new BigDecimal("0").setScale(7, RoundingMode.HALF_UP));
        bean.setPrice(new BigDecimal("12345678901234.1234567"));

        System.out.println("原始对象:");
        System.out.println("  " + bean);
        System.out.println();

        String json = JSON.toJSONString(bean);
        System.out.println("序列化后的 JSON（默认方式）:");
        System.out.println("  " + json);
        System.out.println();

        // 测试8-2：测试对象序列化 - 使用 WriteBigDecimalAsPlain
        System.out.println("================== 对象序列化测试 - 使用 WriteBigDecimalAsPlain ==================");
        String jsonPlain = JSON.toJSONString(bean, SerializerFeature.WriteBigDecimalAsPlain);
        System.out.println("序列化后的 JSON（WriteBigDecimalAsPlain）:");
        System.out.println("  " + jsonPlain);
        System.out.println();

        TestBean deserializedBean = JSON.parseObject(jsonPlain, TestBean.class);
        System.out.println("反序列化后的对象:");
        System.out.println("  " + deserializedBean);
        System.out.println();

        // 验证
        System.out.println("验证结果:");
        System.out.println("  amount 是否相等: " + (bean.getAmount().compareTo(deserializedBean.getAmount()) == 0));
        System.out.println("  amount 原始值: " + bean.getAmount());
        System.out.println("  amount 反序列化值: " + deserializedBean.getAmount());
        System.out.println("  amount toString(): " + bean.getAmount().toString());
        System.out.println("  amount toPlainString(): " + bean.getAmount().toPlainString());
        System.out.println();

        // 测试9：测试 JSONObject 方式 - 默认方式
        System.out.println("================== JSONObject 方式测试 - 默认方式 ==================");
        JSONObject jsonObj = new JSONObject();
        BigDecimal testValue = new BigDecimal("0").setScale(7, RoundingMode.HALF_UP);
        jsonObj.put("amount", testValue);
        String jsonStr = jsonObj.toJSONString();
        System.out.println("JSONObject 序列化（默认方式）:");
        System.out.println("  原始值: " + testValue);
        System.out.println("  toString(): " + testValue.toString());
        System.out.println("  序列化 JSON: " + jsonStr);
        System.out.println();

        // 测试9-2：测试 JSONObject 方式 - 使用 WriteBigDecimalAsPlain
        System.out.println("================== JSONObject 方式测试 - 使用 WriteBigDecimalAsPlain ==================");
        String jsonStrPlain = JSON.toJSONString(jsonObj, SerializerFeature.WriteBigDecimalAsPlain);
        System.out.println("JSONObject 序列化（WriteBigDecimalAsPlain）:");
        System.out.println("  原始值: " + testValue);
        System.out.println("  toString(): " + testValue.toString());
        System.out.println("  序列化 JSON: " + jsonStrPlain);
        System.out.println();

        JSONObject parsedObj = JSON.parseObject(jsonStrPlain);
        Object amountObj = parsedObj.get("amount");
        System.out.println("  反序列化类型: " + amountObj.getClass().getName());
        System.out.println("  反序列化值: " + amountObj);
        if (amountObj instanceof BigDecimal) {
            BigDecimal parsedBd = (BigDecimal) amountObj;
            System.out.println("  parsedBd.toString(): " + parsedBd.toString());
            System.out.println("  parsedBd.toPlainString(): " + parsedBd.toPlainString());
        }
    }

    /**
     * 测试序列化和反序列化
     * @param name 测试名称
     * @param value BigDecimal 值
     * @param useWriteAsPlain 是否使用 WriteBigDecimalAsPlain 特性
     */
    private static void testSerializeAndDeserialize(String name, BigDecimal value, boolean useWriteAsPlain) {
        System.out.println("  原始 BigDecimal:");
        System.out.println("    值: " + value);
        System.out.println("    scale: " + value.scale());
        System.out.println("    precision: " + value.precision());
        System.out.println("    toString(): " + value.toString());
        System.out.println("    toPlainString(): " + value.toPlainString());

        // 序列化
        String json;
        if (useWriteAsPlain) {
            json = JSON.toJSONString(value, SerializerFeature.WriteBigDecimalAsPlain);
            System.out.println("  序列化方式: WriteBigDecimalAsPlain");
        } else {
            json = JSON.toJSONString(value);
            System.out.println("  序列化方式: 默认");
        }
        System.out.println("  序列化后的 JSON: " + json);

        // 检查是否包含科学计数法
        boolean hasScientificNotation = json.contains("E") || json.contains("e");
        if (hasScientificNotation) {
            System.out.println("  ⚠️ JSON 中包含科学计数法");
        } else {
            System.out.println("  ✅ JSON 中不包含科学计数法");
        }

        // 反序列化
        BigDecimal deserialized = JSON.parseObject(json, BigDecimal.class);
        System.out.println("  反序列化后的 BigDecimal:");
        System.out.println("    值: " + deserialized);
        System.out.println("    scale: " + deserialized.scale());
        System.out.println("    precision: " + deserialized.precision());
        System.out.println("    toString(): " + deserialized.toString());
        System.out.println("    toPlainString(): " + deserialized.toPlainString());

        // 验证
        boolean equals = value.compareTo(deserialized) == 0;
        System.out.println("  比较结果: " + (equals ? "✅ 相等" : "❌ 不相等"));

        if (!equals) {
            System.out.println("    ⚠️ 警告：序列化前后值不相等！");
        }
    }

    /**
     * 测试 Bean
     */
    public static class TestBean {
        private Long id;
        private String name;
        private BigDecimal amount;
        private BigDecimal price;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        @Override
        public String toString() {
            return "TestBean{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", amount=" + amount +
                    " (toString=" + (amount != null ? amount.toString() : "null") + ")" +
                    ", price=" + price +
                    '}';
        }
    }
}

