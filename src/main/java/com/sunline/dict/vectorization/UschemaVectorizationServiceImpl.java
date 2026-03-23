package com.sunline.dict.vectorization;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Uschema;
import com.sunline.dict.entity.UschemaDetail;
import com.sunline.dict.mapper.UschemaDetailMapper;
import com.sunline.dict.mapper.UschemaMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uschema（u_schema.xml）限制类型向量化服务实现
 *
 * <p>向量化粒度：以 restriction_type（restriction_type_id）为单位，每个限制类型生成一个 Point。
 * <p>唯一键：uschema_id + restriction_type_id
 *
 * <p>restriction_type_base 共 17 种基本类型，对应 Java 类型：
 * <ul>
 *   <li>String: fixString, string, dateString, dateString8/10, dateTString15/18/19/23, timeString6/8/9/12, clob</li>
 *   <li>BigDecimal: decimal</li>
 *   <li>Integer: int</li>
 *   <li>Long: long</li>
 * </ul>
 *
 * <p>向量化文本格式：
 * <pre>
 * {restrictionTypeId} {restrictionTypeLongname} [所属:{uschemaId}]({uschemaLongname})
 * 基础类型:{base}(Java:{javaType}) 长度:{minLength}-{maxLength} 小数:{fractionDigits} 库长度:{dbLength}
 * </pre>
 */
@Service
@ConditionalOnProperty(name = "vectorization.enabled", havingValue = "true")
public class UschemaVectorizationServiceImpl
        extends AbstractVectorizationBase
        implements UschemaVectorizationService {

    private static final String MODEL_TYPE = "uschema";

    private static final Map<String, String> BASE_TO_JAVA = Map.ofEntries(
            Map.entry("fixString", "String"),
            Map.entry("string", "String"),
            Map.entry("decimal", "BigDecimal"),
            Map.entry("dateString", "String"),
            Map.entry("dateString8", "String"),
            Map.entry("dateTString19", "String"),
            Map.entry("dateTString23", "String"),
            Map.entry("dateTString15", "String"),
            Map.entry("dateTString18", "String"),
            Map.entry("dateString10", "String"),
            Map.entry("timeString8", "String"),
            Map.entry("timeString12", "String"),
            Map.entry("timeString9", "String"),
            Map.entry("timeString6", "String"),
            Map.entry("int", "Integer"),
            Map.entry("long", "Long"),
            Map.entry("clob", "String")
    );

    @Autowired
    private UschemaMapper uschemaMapper;

    @Autowired
    private UschemaDetailMapper uschemaDetailMapper;

    @Override
    public void vectorizeBySource(String sourceInfo) {
        List<Uschema> uschemaList = uschemaMapper.selectList(
                new QueryWrapper<Uschema>().eq("from_jar", sourceInfo));

        if (uschemaList.isEmpty()) {
            log.warn("未找到 from_jar={} 的 uschema 记录，跳过向量化", sourceInfo);
            return;
        }

        log.info("开始向量化 uschema，from_jar={}，共 {} 个文件", sourceInfo, uschemaList.size());

        List<Map<String, Object>> points = new ArrayList<>();

        for (Uschema uschema : uschemaList) {
            try {
                List<UschemaDetail> allDetails = uschemaDetailMapper.selectList(
                        new QueryWrapper<UschemaDetail>().eq("uschema_id", uschema.getId()));

                if (allDetails.isEmpty()) {
                    log.debug("uschema id={} 下无明细，跳过", uschema.getId());
                    continue;
                }

                Set<String> processed = new HashSet<>();

                for (UschemaDetail detail : allDetails) {
                    String restrictionId = detail.getRestrictionTypeId();
                    if (restrictionId == null || restrictionId.isEmpty() || processed.contains(restrictionId))
                        continue;
                    processed.add(restrictionId);

                    String text = buildVectorText(uschema, detail);
                    List<Float> vector = embeddingClient.embed(text);

                    Map<String, Object> point = new HashMap<>();
                    point.put("id", toUuidString("uschema:" + uschema.getId() + ":" + restrictionId));
                    point.put("vector", vector);
                    point.put("payload", buildPayload(uschema, detail, text, sourceInfo));
                    points.add(point);

                    log.debug("uschema 向量化完成：uschemaId={}, restrictionId={}",
                            uschema.getId(), restrictionId);
                }

            } catch (Exception e) {
                log.error("uschema 向量化失败：id={}，错误：{}", uschema.getId(), e.getMessage(), e);
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("uschema 向量写入完成，from_jar={}，成功 {} 个限制类型", sourceInfo, points.size());
        }
    }

    @Override
    public void deleteBySource(String sourceInfo) {
        deleteBySourceInternal(MODEL_TYPE, sourceInfo);
    }

    // ─────────────── 私有方法 ───────────────

    /**
     * 构建向量化文本
     *
     * <p>示例：
     * <pre>
     * U_IP_ADDR IP地址 [所属:MBaseType](基础限制类型)
     * 基础类型:string(Java:String) 长度:1-15 库长度:15
     * </pre>
     *
     * <pre>
     * U_AMT 金额 [所属:MBaseType](基础限制类型)
     * 基础类型:decimal(Java:BigDecimal) 长度:1-17 小数:2 库长度:17
     * </pre>
     */
    private String buildVectorText(Uschema uschema, UschemaDetail detail) {
        StringBuilder sb = new StringBuilder();

        // 1. 限制类型基本信息
        if (notEmpty(detail.getRestrictionTypeId())) {
            sb.append(detail.getRestrictionTypeId());
        }
        if (notEmpty(detail.getRestrictionTypeLongname())) {
            sb.append(" ").append(detail.getRestrictionTypeLongname());
        }
        if (notEmpty(uschema.getId())) {
            sb.append(" [所属:").append(uschema.getId()).append("]");
        }
        if (notEmpty(uschema.getLongname())) {
            sb.append("(").append(uschema.getLongname()).append(")");
        }

        // 2. 基础类型 + Java 映射
        if (notEmpty(detail.getRestrictionTypeBase())) {
            String base = detail.getRestrictionTypeBase();
            String javaType = BASE_TO_JAVA.getOrDefault(base, "Object");
            sb.append(" 基础类型:").append(base).append("(Java:").append(javaType).append(")");
        }

        // 3. 长度信息
        boolean hasMin = notEmpty(detail.getRestrictionTypeMinLength());
        boolean hasMax = notEmpty(detail.getRestrictionTypeMaxLength());
        if (hasMin || hasMax) {
            sb.append(" 长度:");
            if (hasMin) sb.append(detail.getRestrictionTypeMinLength());
            if (hasMin && hasMax) sb.append("-");
            if (hasMax) sb.append(detail.getRestrictionTypeMaxLength());
        }

        // 4. 小数位
        if (notEmpty(detail.getRestrictionTypeFractionDigits())) {
            sb.append(" 小数:").append(detail.getRestrictionTypeFractionDigits());
        }

        // 5. 数据库字段长度
        if (notEmpty(detail.getRestrictionTypeDbLength())) {
            sb.append(" 库长度:").append(detail.getRestrictionTypeDbLength());
        }

        return sb.toString();
    }

    /**
     * 构建 Qdrant payload
     * uschema 是叶子节点，ref_ids 为空
     */
    private Map<String, Object> buildPayload(Uschema uschema, UschemaDetail detail,
                                              String text, String sourceInfo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_type", MODEL_TYPE);
        payload.put("uschema_id", uschema.getId());
        payload.put("record_id", uschema.getId() + ":" + detail.getRestrictionTypeId());
        payload.put("restriction_type_id", detail.getRestrictionTypeId() != null ? detail.getRestrictionTypeId() : "");
        payload.put("longname", detail.getRestrictionTypeLongname() != null ? detail.getRestrictionTypeLongname() : "");
        payload.put("base_type", detail.getRestrictionTypeBase() != null ? detail.getRestrictionTypeBase() : "");
        payload.put("java_type", BASE_TO_JAVA.getOrDefault(detail.getRestrictionTypeBase(), "Object"));
        payload.put("from_jar", sourceInfo);
        payload.put("text", text);
        payload.put("ref_ids", List.of()); // 叶子节点，无下游依赖
        return payload;
    }
}
