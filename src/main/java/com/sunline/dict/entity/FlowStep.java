package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程步骤实体类
 */
@TableName("flow_step")
public class FlowStep implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 流程ID（关联flowtran表的id）
     */
    private String flowId;
    
    /**
     * 节点名称
     */
    private String nodeName;
    
    /**
     * 节点类型（service/method）
     */
    private String nodeType;
    
    /**
     * 步骤顺序
     */
    private Integer step;
    
    /**
     * 节点长名称（longname属性）
     */
    private String nodeLongname;
    
    /**
     * 错误调用列表（违规的分层或领域，逗号分割）
     */
    private String incorrectCalls;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    // Getter and Setter methods
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getFlowId() {
        return flowId;
    }
    
    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }
    
    public String getNodeName() {
        return nodeName;
    }
    
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }
    
    public String getNodeType() {
        return nodeType;
    }
    
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }
    
    public Integer getStep() {
        return step;
    }
    
    public void setStep(Integer step) {
        this.step = step;
    }
    
    public String getNodeLongname() {
        return nodeLongname;
    }
    
    public void setNodeLongname(String nodeLongname) {
        this.nodeLongname = nodeLongname;
    }
    
    public String getIncorrectCalls() {
        return incorrectCalls;
    }
    
    public void setIncorrectCalls(String incorrectCalls) {
        this.incorrectCalls = incorrectCalls;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    @Override
    public String toString() {
        return "FlowStep{" +
                "id=" + id +
                ", flowId='" + flowId + '\'' +
                ", nodeName='" + nodeName + '\'' +
                ", nodeType='" + nodeType + '\'' +
                ", step=" + step +
                ", nodeLongname='" + nodeLongname + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}

