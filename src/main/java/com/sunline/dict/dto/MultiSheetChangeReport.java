package com.sunline.dict.dto;

/**
 * 多Sheet变更报告
 */
public class MultiSheetChangeReport {
    
    private ChangeReport dictChangeReport;          // 字典技术衍生表变更报告
    private ChangeReport domainChangeReport;        // 域清单变更报告
    private ChangeReport codeExtensionChangeReport; // 代码扩展清单变更报告
    
    public MultiSheetChangeReport() {
    }
    
    public MultiSheetChangeReport(ChangeReport dictChangeReport, 
                                  ChangeReport domainChangeReport, 
                                  ChangeReport codeExtensionChangeReport) {
        this.dictChangeReport = dictChangeReport;
        this.domainChangeReport = domainChangeReport;
        this.codeExtensionChangeReport = codeExtensionChangeReport;
    }
    
    // Getters and Setters
    public ChangeReport getDictChangeReport() {
        return dictChangeReport;
    }
    
    public void setDictChangeReport(ChangeReport dictChangeReport) {
        this.dictChangeReport = dictChangeReport;
    }
    
    public ChangeReport getDomainChangeReport() {
        return domainChangeReport;
    }
    
    public void setDomainChangeReport(ChangeReport domainChangeReport) {
        this.domainChangeReport = domainChangeReport;
    }
    
    public ChangeReport getCodeExtensionChangeReport() {
        return codeExtensionChangeReport;
    }
    
    public void setCodeExtensionChangeReport(ChangeReport codeExtensionChangeReport) {
        this.codeExtensionChangeReport = codeExtensionChangeReport;
    }
    
    /**
     * 获取总的变更统计
     */
    public ChangeSummary getTotalSummary() {
        ChangeSummary total = new ChangeSummary();
        
        // 初始化为0，避免空指针
        total.setNewCount(0);
        total.setUpdateCount(0);
        total.setDeleteCount(0);
        total.setUnchangedCount(0);
        total.setTotalCount(0);
        
        if (dictChangeReport != null && dictChangeReport.getSummary() != null) {
            ChangeSummary dict = dictChangeReport.getSummary();
            total.setNewCount(total.getNewCount() + (dict.getNewCount() != null ? dict.getNewCount() : 0));
            total.setUpdateCount(total.getUpdateCount() + (dict.getUpdateCount() != null ? dict.getUpdateCount() : 0));
            total.setDeleteCount(total.getDeleteCount() + (dict.getDeleteCount() != null ? dict.getDeleteCount() : 0));
            total.setUnchangedCount(total.getUnchangedCount() + (dict.getUnchangedCount() != null ? dict.getUnchangedCount() : 0));
        }
        
        if (domainChangeReport != null && domainChangeReport.getSummary() != null) {
            ChangeSummary domain = domainChangeReport.getSummary();
            total.setNewCount(total.getNewCount() + (domain.getNewCount() != null ? domain.getNewCount() : 0));
            total.setUpdateCount(total.getUpdateCount() + (domain.getUpdateCount() != null ? domain.getUpdateCount() : 0));
            total.setDeleteCount(total.getDeleteCount() + (domain.getDeleteCount() != null ? domain.getDeleteCount() : 0));
            total.setUnchangedCount(total.getUnchangedCount() + (domain.getUnchangedCount() != null ? domain.getUnchangedCount() : 0));
        }
        
        if (codeExtensionChangeReport != null && codeExtensionChangeReport.getSummary() != null) {
            ChangeSummary code = codeExtensionChangeReport.getSummary();
            total.setNewCount(total.getNewCount() + (code.getNewCount() != null ? code.getNewCount() : 0));
            total.setUpdateCount(total.getUpdateCount() + (code.getUpdateCount() != null ? code.getUpdateCount() : 0));
            total.setDeleteCount(total.getDeleteCount() + (code.getDeleteCount() != null ? code.getDeleteCount() : 0));
            total.setUnchangedCount(total.getUnchangedCount() + (code.getUnchangedCount() != null ? code.getUnchangedCount() : 0));
        }
        
        // 计算总数
        total.setTotalCount(total.getNewCount() + total.getUpdateCount() + total.getUnchangedCount());
        
        return total;
    }
    
    @Override
    public String toString() {
        return "MultiSheetChangeReport{" +
                "dictChangeReport=" + dictChangeReport +
                ", domainChangeReport=" + domainChangeReport +
                ", codeExtensionChangeReport=" + codeExtensionChangeReport +
                '}';
    }
}

