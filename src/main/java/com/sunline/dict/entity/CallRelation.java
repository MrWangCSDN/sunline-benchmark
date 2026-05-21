package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("call_relation")
public class CallRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String callerId;
    private String callerType;
    private String callerMethod;
    private String callerLongname;
    private String callerDomain;
    private String callerServiceId;

    private String calleeId;
    private String calleeType;
    private String calleeMethod;
    private String calleeLongname;
    private String calleeDomain;
    private String calleeClass;
    private String calleeServiceId;
    private String calleeServiceName;

    private String fromJar;
    private Integer isDirect;
    private Integer crossDomain;
    private Integer ruleViolation;
    private String violationDesc;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }

    public String getCallerType() { return callerType; }
    public void setCallerType(String callerType) { this.callerType = callerType; }

    public String getCallerMethod() { return callerMethod; }
    public void setCallerMethod(String callerMethod) { this.callerMethod = callerMethod; }

    public String getCallerLongname() { return callerLongname; }
    public void setCallerLongname(String callerLongname) { this.callerLongname = callerLongname; }

    public String getCallerDomain() { return callerDomain; }
    public void setCallerDomain(String callerDomain) { this.callerDomain = callerDomain; }

    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String callerServiceId) { this.callerServiceId = callerServiceId; }

    public String getCalleeId() { return calleeId; }
    public void setCalleeId(String calleeId) { this.calleeId = calleeId; }

    public String getCalleeType() { return calleeType; }
    public void setCalleeType(String calleeType) { this.calleeType = calleeType; }

    public String getCalleeMethod() { return calleeMethod; }
    public void setCalleeMethod(String calleeMethod) { this.calleeMethod = calleeMethod; }

    public String getCalleeLongname() { return calleeLongname; }
    public void setCalleeLongname(String calleeLongname) { this.calleeLongname = calleeLongname; }

    public String getCalleeDomain() { return calleeDomain; }
    public void setCalleeDomain(String calleeDomain) { this.calleeDomain = calleeDomain; }

    public String getCalleeClass() { return calleeClass; }
    public void setCalleeClass(String calleeClass) { this.calleeClass = calleeClass; }

    public String getCalleeServiceId() { return calleeServiceId; }
    public void setCalleeServiceId(String calleeServiceId) { this.calleeServiceId = calleeServiceId; }

    public String getCalleeServiceName() { return calleeServiceName; }
    public void setCalleeServiceName(String calleeServiceName) { this.calleeServiceName = calleeServiceName; }

    public String getFromJar() { return fromJar; }
    public void setFromJar(String fromJar) { this.fromJar = fromJar; }

    public Integer getIsDirect() { return isDirect; }
    public void setIsDirect(Integer isDirect) { this.isDirect = isDirect; }

    public Integer getCrossDomain() { return crossDomain; }
    public void setCrossDomain(Integer crossDomain) { this.crossDomain = crossDomain; }

    public Integer getRuleViolation() { return ruleViolation; }
    public void setRuleViolation(Integer ruleViolation) { this.ruleViolation = ruleViolation; }

    public String getViolationDesc() { return violationDesc; }
    public void setViolationDesc(String violationDesc) { this.violationDesc = violationDesc; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
