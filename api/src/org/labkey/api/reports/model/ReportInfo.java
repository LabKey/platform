package org.labkey.api.reports.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.study.DatasetDB;

import java.io.IOException;
import java.util.Date;

public class ReportInfo
{
    private final String _name;
    private final Date _modified;
    private final String _containerId;
    private final int _categoryId;
    private final String _type;
    private final Integer _rowId;
    private final Report _report;
    private final String _status;
    private final int _displayOrder;

    public ReportInfo(ReportDB reportDB)
    {
        try
        {
            ReportDescriptor reportDescriptor = ReportDescriptor.createFromXML(reportDB.getDescriptorXML());
            _report = ReportService.get().createReportInstance(reportDescriptor);
            _type = _report.getTypeDescription();
            _name = reportDescriptor.getReportName();
            _modified = reportDB.getModified();
            _containerId = reportDB.getContainerId();
            _rowId = reportDB.getRowId();
            _displayOrder = reportDB.getDisplayOrder();
            Integer categoryId = reportDB.getCategoryId();
            Container container = ContainerManager.getForId(_containerId);
            if (null != container)
            {
                _status = (String)ReportPropsManager.get().getPropertyValue(reportDB.getEntityId(), container, "status");

                if (null == categoryId)
                {
                    String schemaName = reportDescriptor.getProperty(ReportDescriptor.Prop.schemaName);
                    String queryName = reportDescriptor.getProperty(ReportDescriptor.Prop.queryName);
                    if (null != schemaName && null != queryName)
                    {
                        {
                            ViewCategory category = ReportUtil.getDefaultCategory(container, schemaName, queryName);
                            categoryId = category.getRowId();
                        }
                    }
                }
                _categoryId = null != categoryId ? categoryId : ViewCategoryManager.UNCATEGORIZED_ROWID;
            }
            else
            {
                _categoryId = 0;
                _status = "";
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    public ReportInfo(DatasetDB dataset)
    {
        _name = dataset.getName();
        _type = "Dataset";
        _modified = dataset.getModified();
        _containerId = dataset.getContainer();
        _rowId = dataset.getDatasetId();
        _categoryId = null != dataset.getCategoryId() ? dataset.getCategoryId() : ViewCategoryManager.UNCATEGORIZED_ROWID;
        _displayOrder = dataset.getDisplayOrder();
        _report = null;
        Container container = ContainerManager.getForId(_containerId);
        _status = (null != container) ?
                (String)ReportPropsManager.get().getPropertyValue(dataset.getEntityId(), container, "status") : "";
    }

    public String getName()
    {
        return _name;
    }

    public Date getModified()
    {
        return _modified;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public int getCategoryId()
    {
        return _categoryId;
    }

    public String getType()
    {
        return _type;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public Report getReport()
    {
        return _report;
    }

    public String getStatus()
    {
        return _status;
    }

    public int getDisplayOrder()
    {
        return _displayOrder;
    }

    public enum Type
    {
        report,
        dataset
    }
}
