package org.labkey.api.reports.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.study.DatasetDB;

import java.io.IOException;
import java.util.Date;

public class ReportInfo
{
    private String _name;
    private Date _created;
    private Date _modified;
    private String _containerId;
    private int _categoryId;
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
            setRowId(report.getRowId());

            Integer categoryId = report.getCategoryId();
            if (null == categoryId)
            {
                String schemaName = reportDescriptor.getProperty(ReportDescriptor.Prop.schemaName);
                String queryName = reportDescriptor.getProperty(ReportDescriptor.Prop.queryName);
                if (null != schemaName && null != queryName)
                {
                    Container container = ContainerManager.getForId(report.getContainerId());
                    if (null != container)
                    {
                        ViewCategory category = ReportUtil.getDefaultCategory(container, schemaName, queryName);
                        categoryId = category.getRowId();
                    }
                }
            }
            setCategoryId(null != categoryId ? categoryId : ViewCategoryManager.UNCATEGORIZED_ROWID);
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
        setRowId(dataset.getDatasetId());
        setCategoryId(null != dataset.getCategoryId() ? dataset.getCategoryId() : ViewCategoryManager.UNCATEGORIZED_ROWID);
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

    public int getCategoryId()
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
