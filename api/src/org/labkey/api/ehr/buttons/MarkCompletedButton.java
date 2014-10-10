/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.ehr.buttons;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.security.EHRCompletedUpdatePermission;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;

/**
 * User: bimber
 * Date: 7/14/13
 * Time: 4:05 PM
 */
public class MarkCompletedButton extends SimpleButtonConfigFactory
{
    protected String _schemaName;
    protected String _queryName;
    protected Boolean _forceDateOnlyField;
    private Class<? extends Permission> _perm;

    public MarkCompletedButton(Module owner, String schemaName, String queryName)
    {
        this(owner, schemaName, queryName, "Mark Completed");
    }

    public MarkCompletedButton(Module owner, String schemaName, String queryName, String label)
    {
        this(owner, schemaName, queryName, label, EHRCompletedUpdatePermission.class, false);
    }

    public MarkCompletedButton(Module owner, String schemaName, String queryName, String label, boolean forceDateOnlyField)
    {
        this(owner, schemaName, queryName, label, EHRCompletedUpdatePermission.class, forceDateOnlyField);
    }

    public MarkCompletedButton(Module owner, String schemaName, String queryName, String label, Class<? extends Permission> perm)
    {
        this(owner, schemaName, queryName, label, perm, false);
    }

    public MarkCompletedButton(Module owner, String schemaName, String queryName, String label, Class<? extends Permission> perm, boolean forceDateOnlyField)
    {
        super(owner, label, "");
        setClientDependencies(ClientDependency.fromModuleName("ehr"));
        _schemaName = schemaName;
        _queryName = queryName;
        _perm = perm;
        _forceDateOnlyField = forceDateOnlyField;
    }

    public boolean isAvailable(TableInfo ti)
    {
        if (!super.isAvailable(ti))
            return false;

        if (ti.getUserSchema().getName().equalsIgnoreCase(_schemaName) && ti.getPublicName().equalsIgnoreCase(_queryName))
            return EHRService.get().hasPermission(ti, _perm);

        return false;
    }

    @Override
    protected String getJsHandler(TableInfo ti)
    {
        // NOTE: we have a problem if this is called before all XML metadata is applied.  for example, a dataset could call the tablecustomizer from studyData.query.xml
        // but the dataset-specific query.xml file will apply a different format to the enddate column.  as a result, defer creating the JS handler as long as we can.
        ColumnInfo col = ti.getColumn("enddate");
        String xtype = "datefield";
        if (!_forceDateOnlyField && col != null && col.getFormat() != null && col.getFormat().contains("HH"))
            xtype = "xdatetime";

        String pkColName = null;
        if (ti.getPkColumnNames() != null && ti.getPkColumnNames().size() == 1)
        {
            pkColName = ti.getPkColumnNames().get(0);
        }

        return "EHR.window.MarkCompletedWindow.buttonHandler(dataRegionName, " + PageFlowUtil.jsString(_schemaName) + ", " + PageFlowUtil.jsString(_queryName) + ", " + PageFlowUtil.jsString(xtype) + ", " + PageFlowUtil.jsString(pkColName) + ");";
    }
}
