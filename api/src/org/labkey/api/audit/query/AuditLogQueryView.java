/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.audit.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Sep 19, 2007
 */
public abstract class AuditLogQueryView extends QueryView
{
    protected List<String> _columns = new ArrayList<String>();
    protected List<DisplayColumn> _displayColumns = new ArrayList<DisplayColumn>();
    protected SimpleFilter _filter;
    protected Sort _sort;
    protected String _title;
    protected Map<String, AuditDisplayColumnFactory> _displayColFactory = new HashMap<String, AuditDisplayColumnFactory>();

    public AuditLogQueryView(UserSchema schema, QuerySettings settings, SimpleFilter filter)
    {
        super(schema, settings);
        _filter = filter;
        _buttonBarPosition = DataRegion.ButtonBarPosition.NONE;
    }

    public AuditLogQueryView(ViewContext context)
    {
        super((UserSchema)null);

        _buttonBarPosition = DataRegion.ButtonBarPosition.NONE;
        UserSchema schema = AuditLogService.get().createSchema(context.getUser(), context.getContainer());
        String tableName = AuditLogService.get().getTableName();
        QuerySettings settings = new QuerySettings(context, tableName);
        settings.setSchemaName(schema.getSchemaName());
        settings.setQueryName(tableName);

        setSchema(schema);
        setSettings(settings);
    }

    public void setVisibleColumns(String[] columnNames)
    {
        for (String name : columnNames)
            _columns.add(name.toLowerCase());
    }

    public void setFilter(SimpleFilter filter)
    {
        _filter = filter;
    }
    
    public void setSort(Sort sort)
    {
        _sort = sort;
    }

    public void addDisplayColumn(DisplayColumn dc)
    {
        _displayColumns.add(dc);
    }

    public abstract void addDisplayColumn(int index, DisplayColumn dc);

    public void setTitle(String title)
    {
        _title = title;
    }
}

