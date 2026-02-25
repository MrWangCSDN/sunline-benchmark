package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 域清单映射关系实体
 * 用于维护非代码类的域英文简称和域中文名称的映射关系
 */
@TableName("domain_mapping")
public class DomainMapping {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String domainChineseName;           // 域中文名称
    private String domainEnglishAbbr;           // 域英文简称
    private LocalDateTime createTime;           // 创建时间
    private LocalDateTime updateTime;           // 更新时间
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getDomainChineseName() {
        return domainChineseName;
    }
    
    public void setDomainChineseName(String domainChineseName) {
        this.domainChineseName = domainChineseName;
    }
    
    public String getDomainEnglishAbbr() {
        return domainEnglishAbbr;
    }
    
    public void setDomainEnglishAbbr(String domainEnglishAbbr) {
        this.domainEnglishAbbr = domainEnglishAbbr;
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
        return "DomainMapping{" +
                "id=" + id +
                ", domainChineseName='" + domainChineseName + '\'' +
                ", domainEnglishAbbr='" + domainEnglishAbbr + '\'' +
                '}';
    }
}

