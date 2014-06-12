package org.labkey.api.reports.model;

import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.study.DatasetDB;

import java.io.IOException;
import java.util.Date;

public class ReportInfo
{
    private String _name;
    private Date _created;
    private Date _modified;
    private String _containerId;
    private Integer _categoryId;
    private Type _type;
    private Integer rowId;

    public ReportInfo(String name, Type type)
    {
        _name = name;
        _type = type;
    }

    public ReportInfo(ReportDB report)
    {
        try
        {
            _type = Type.report;
            ReportDescriptor reportDescriptor = ReportDescriptor.createFromXML(report.getDescriptorXML());
            _name = reportDescriptor.getReportName();
            setCreated(report.getCreated());
            setModified(report.getModified());
            setContainerId(report.getContainerId());
            setCategoryId(null != report.getCategoryId() ? report.getCategoryId() : ViewCategoryManager.UNCATEGORIZED_ROWID);
            setRowId(report.getRowId());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    public ReportInfo(DatasetDB dataset)
    {
        this(dataset.getName(), Type.dataset);
        setModified(dataset.getModified());
        setContainerId(dataset.getContainer());
        setCategoryId(null != dataset.getCategoryId() ? dataset.getCategoryId() : ViewCategoryManager.UNCATEGORIZED_ROWID);
        setRowId(dataset.getDatasetId());
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public void setContainerId(String containerId)
    {
        _containerId = containerId;
    }

    public Integer getCategoryId()
    {
        return _categoryId;
    }

    public void setCategoryId(Integer categoryId)
    {
        _categoryId = categoryId;
    }

    public Type getType()
    {
        return _type;
    }

    public void setType(Type type)
    {
        _type = type;
    }

    public Integer getRowId()
    {
        return rowId;
    }

    public void setRowId(Integer rowId)
    {
        this.rowId = rowId;
    }

    public enum Type
    {
        report,
        dataset
    }

}
