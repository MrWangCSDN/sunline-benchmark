# Fastjson BigDecimal 序列化测试说明

## 问题验证

当使用 Fastjson 序列化 BigDecimal 时，如果值为 0 且 scale >= 7，会出现科学计数法格式（如 `0E-7`）。

## 测试步骤

### 1. 添加 Fastjson 依赖

在 `pom.xml` 中添加以下依赖之一：

**Fastjson 1.x（推荐用于测试）：**
```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
    <version>1.2.83</version>
</dependency>
```

**Fastjson 2.x（新版本）：**
```xml
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.43</version>
</dependency>
```

### 2. 运行测试

编译并运行 `FastjsonBigDecimalTest.java`：

```bash
javac -cp ".:fastjson-1.2.83.jar" FastjsonBigDecimalTest.java
java -cp ".:fastjson-1.2.83.jar" FastjsonBigDecimalTest
```

或使用 Maven：

```bash
mvn compile exec:java -Dexec.mainClass="FastjsonBigDecimalTest"
```

## 预期测试结果

### 测试1：默认序列化（值为0，scale=7）

**Fastjson 1.x 行为：**
```json
{"zeroValue":0E-7,"amount":12345678901234.1234567,"price":1E-7}
```
❌ **包含科学计数法**

**Fastjson 2.x 行为：**
```json
{"zeroValue":0E-7,"amount":12345678901234.1234567,"price":1E-7}
```
❌ **包含科学计数法**

### 测试2：使用 WriteBigDecimalAsString 特性

**Fastjson 1.x：**
```java
String json = JSON.toJSONString(entity, SerializerFeature.WriteBigDecimalAsString);
```
结果：
```json
{"zeroValue":"0.0000000","amount":"12345678901234.1234567","price":"0.0000001"}
```
✅ **不包含科学计数法，但所有 BigDecimal 都转为字符串**

**Fastjson 2.x：**
```java
String json = JSON.toJSONString(entity, JSONWriter.Feature.WriteBigDecimalAsString);
```
结果：
```json
{"zeroValue":"0.0000000","amount":"12345678901234.1234567","price":"0.0000001"}
```
✅ **不包含科学计数法，但所有 BigDecimal 都转为字符串**

### 测试3：反序列化

Fastjson 可以正确反序列化科学计数法格式：

```java
String json = "{\"zeroValue\":\"0E-7\"}";
TestEntity entity = JSON.parseObject(json, TestEntity.class);
// entity.getZeroValue() 可以正确解析
// 但 entity.getZeroValue().toString() 仍会显示 "0E-7"
```

## 解决方案

### 方案1：全局配置 WriteBigDecimalAsString（推荐）

**Fastjson 1.x：**
```java
// 方式1：每次序列化时指定
String json = JSON.toJSONString(entity, SerializerFeature.WriteBigDecimalAsString);

// 方式2：全局配置（需要在启动时配置）
SerializeConfig config = new SerializeConfig();
// 配置全局序列化特性
```

**Fastjson 2.x：**
```java
// 方式1：每次序列化时指定
String json = JSON.toJSONString(entity, JSONWriter.Feature.WriteBigDecimalAsString);

// 方式2：全局配置
JSONWriter.Feature[] features = {JSONWriter.Feature.WriteBigDecimalAsString};
```

### 方案2：自定义序列化器

**Fastjson 1.x：**
```java
public class BigDecimalSerializer implements ObjectSerializer {
    @Override
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        if (object == null) {
            serializer.writeNull();
            return;
        }
        BigDecimal bd = (BigDecimal) object;
        if (bd.compareTo(BigDecimal.ZERO) == 0) {
            serializer.write("0");
        } else {
            serializer.write(bd.toPlainString());
        }
    }
}

// 注册序列化器
SerializeConfig config = new SerializeConfig();
config.put(BigDecimal.class, new BigDecimalSerializer());
```

**Fastjson 2.x：**
```java
public class BigDecimalWriter implements ObjectWriter<BigDecimal> {
    @Override
    public void write(JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
        if (object == null) {
            jsonWriter.writeNull();
            return;
        }
        BigDecimal bd = (BigDecimal) object;
        if (bd.compareTo(BigDecimal.ZERO) == 0) {
            jsonWriter.writeString("0");
        } else {
            jsonWriter.writeString(bd.toPlainString());
        }
    }
}
```

### 方案3：在实体类字段上使用注解

**Fastjson 1.x：**
```java
public class TestEntity {
    @JSONField(serializeUsing = BigDecimalSerializer.class)
    private BigDecimal amount;
}
```

**Fastjson 2.x：**
```java
public class TestEntity {
    @JSONField(serializeUsing = BigDecimalWriter.class)
    private BigDecimal amount;
}
```

### 方案4：业务层统一处理（最灵活）

在返回给前端之前，统一使用工具类处理：

```java
public class BigDecimalUtil {
    public static String toString(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return value.toPlainString();
    }
}

// 在 DTO 中使用
public class ResponseDTO {
    private String amount;  // 使用 String 类型
    
    public void setAmount(BigDecimal amount) {
        this.amount = BigDecimalUtil.toString(amount);
    }
}
```

## 实际测试代码示例

### 完整测试类（Fastjson 1.x）

```java
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class FastjsonTest {
    static class Entity {
        private BigDecimal zeroValue;
        
        public BigDecimal getZeroValue() {
            return zeroValue;
        }
        
        public void setZeroValue(BigDecimal zeroValue) {
            this.zeroValue = zeroValue;
        }
    }
    
    public static void main(String[] args) {
        Entity entity = new Entity();
        entity.setZeroValue(BigDecimal.ZERO.setScale(7, RoundingMode.HALF_UP));
        
        // 默认序列化
        String json1 = JSON.toJSONString(entity);
        System.out.println("默认序列化: " + json1);
        // 输出: {"zeroValue":0E-7}  ❌
        
        // 使用 WriteBigDecimalAsString
        String json2 = JSON.toJSONString(entity, SerializerFeature.WriteBigDecimalAsString);
        System.out.println("WriteBigDecimalAsString: " + json2);
        // 输出: {"zeroValue":"0.0000000"}  ✅
        
        // 反序列化
        String json3 = "{\"zeroValue\":\"0E-7\"}";
        Entity entity2 = JSON.parseObject(json3, Entity.class);
        System.out.println("反序列化后: " + entity2.getZeroValue());
        System.out.println("toString(): " + entity2.getZeroValue().toString());
        // 输出: toString(): 0E-7  ❌
        System.out.println("toPlainString(): " + entity2.getZeroValue().toPlainString());
        // 输出: toPlainString(): 0.0000000  ✅
    }
}
```

## 总结

1. **Fastjson 默认行为**：当 BigDecimal 的 scale >= 7 且值为 0 时，会使用科学计数法（`0E-7`）
2. **解决方案**：使用 `WriteBigDecimalAsString` 特性，将 BigDecimal 序列化为字符串
3. **反序列化**：Fastjson 可以正确反序列化科学计数法格式，但需要在业务层使用 `toPlainString()` 处理
4. **推荐方案**：结合使用 `WriteBigDecimalAsString` 特性和业务层工具类，确保前后端数据格式一致

