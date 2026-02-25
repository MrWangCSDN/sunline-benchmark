package com.sunline.dict.dto;

import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.entity.DomainData;

import java.util.List;

/**
 * 多Sheet数据封装类
 */
public class MultiSheetData {
    
    private List<DictData> dictDataList;                    // 字典技术衍生表数据
    private List<DomainData> domainDataList;                // 域清单数据
    private List<CodeExtensionData> codeExtensionDataList;  // 代码扩展清单数据
    
    private int dictDataCount;
    private int domainDataCount;
    private int codeExtensionDataCount;
    
    public MultiSheetData() {
    }
    
    public MultiSheetData(List<DictData> dictDataList, List<DomainData> domainDataList, 
                          List<CodeExtensionData> codeExtensionDataList) {
        this.dictDataList = dictDataList;
        this.domainDataList = domainDataList;
        this.codeExtensionDataList = codeExtensionDataList;
        this.dictDataCount = dictDataList != null ? dictDataList.size() : 0;
        this.domainDataCount = domainDataList != null ? domainDataList.size() : 0;
        this.codeExtensionDataCount = codeExtensionDataList != null ? codeExtensionDataList.size() : 0;
    }
    
    // Getters and Setters
    public List<DictData> getDictDataList() {
        return dictDataList;
    }
    
    public void setDictDataList(List<DictData> dictDataList) {
        this.dictDataList = dictDataList;
        this.dictDataCount = dictDataList != null ? dictDataList.size() : 0;
    }
    
    public List<DomainData> getDomainDataList() {
        return domainDataList;
    }
    
    public void setDomainDataList(List<DomainData> domainDataList) {
        this.domainDataList = domainDataList;
        this.domainDataCount = domainDataList != null ? domainDataList.size() : 0;
    }
    
    public List<CodeExtensionData> getCodeExtensionDataList() {
        return codeExtensionDataList;
    }
    
    public void setCodeExtensionDataList(List<CodeExtensionData> codeExtensionDataList) {
        this.codeExtensionDataList = codeExtensionDataList;
        this.codeExtensionDataCount = codeExtensionDataList != null ? codeExtensionDataList.size() : 0;
    }
    
    public int getDictDataCount() {
        return dictDataCount;
    }
    
    public int getDomainDataCount() {
        return domainDataCount;
    }
    
    public int getCodeExtensionDataCount() {
        return codeExtensionDataCount;
    }
    
    public int getTotalCount() {
        return dictDataCount + domainDataCount + codeExtensionDataCount;
    }
    
    @Override
    public String toString() {
        return "MultiSheetData{" +
                "dictDataCount=" + dictDataCount +
                ", domainDataCount=" + domainDataCount +
                ", codeExtensionDataCount=" + codeExtensionDataCount +
                ", totalCount=" + getTotalCount() +
                '}';
    }
}

