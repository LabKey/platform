/*
 * Copyright (c) 2014-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.reports.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.study.DatasetDB;

import java.util.Date;

public class NotificationInfo
{
    private final String _name;
    private final Date _modified;
    private final Container _container;
    private final int _categoryId;
    private final String _type;
    private final Integer _rowId;
    private final Report _report;
    private final String _status;
    private final int _displayOrder;
    private final boolean _hidden;
    private final boolean _shared;

    public NotificationInfo(ReportDB reportDB)
    {
        try
        {
            _report = ReportService.get().getReport(reportDB);
            if (null == _report)
                throw new IllegalStateException("Expected to get report.");
            ReportDescriptor reportDescriptor = _report.getDescriptor();
            _type = _report.getTypeDescription();
            _name = reportDescriptor.getReportName();
            _modified = reportDB.getModified();
            _rowId = reportDB.getRowId();
            _displayOrder = reportDB.getDisplayOrder();
            Integer categoryId = reportDB.getCategoryId();
            _container = ContainerManager.getForId(reportDB.getContainerId());
            if (null != _container)
            {
                _status = reportDescriptor.getStatus();
                _hidden = reportDescriptor.isHidden();
                _shared = reportDescriptor.isShared();

                if (null == categoryId)
                {
                    String schemaName = reportDescriptor.getProperty(ReportDescriptor.Prop.schemaName);
                    String queryName = reportDescriptor.getProperty(ReportDescriptor.Prop.queryName);
                    if (null != schemaName && null != queryName)
                    {
                        ViewCategory category = ReportUtil.getDefaultCategory(_container, schemaName, queryName);
                        categoryId = category.getRowId();
                    }
                }
                _categoryId = null != categoryId ? categoryId : ViewCategoryManager.UNCATEGORIZED_ROWID;
            }
            else
            {
                _categoryId = 0;
                _status = "";
                _hidden = false;
                _shared = true;
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public NotificationInfo(DatasetDB dataset, boolean isHidden)
    {
        _name = dataset.getName();
        _type = "Dataset";
        _modified = dataset.getModified();
        _rowId = dataset.getDatasetId();
        _categoryId = null != dataset.getCategoryId() ? dataset.getCategoryId() : ViewCategoryManager.UNCATEGORIZED_ROWID;
        _displayOrder = dataset.getDisplayOrder();
        _report = null;
        _hidden = isHidden;
        _shared = true;             // shared datasets means something different.
        _container = ContainerManager.getForId(dataset.getContainer());
        _status = (null != _container) ?
                (String)ReportPropsManager.get().getPropertyValue(dataset.getEntityId(), _container, "status") : "";
    }

    public String getName()
    {
        return _name;
    }

    public Date getModified()
    {
        return _modified;
    }

    public Container getContainer()
    {
        return _container;
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

    public boolean isHidden()
    {
        return _hidden;
    }

    public boolean isShared()
    {
        return _shared;
    }

    public enum Type
    {
        report,
        dataset
    }
}
