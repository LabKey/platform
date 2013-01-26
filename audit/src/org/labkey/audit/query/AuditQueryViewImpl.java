/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.audit.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.query.AuditDisplayColumnFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.DataView;
import org.labkey.audit.model.LogManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
public class AuditQueryViewImpl extends AuditLogQueryView
{
    protected Map<Integer, DisplayColumn> _indexedColumns = new HashMap<Integer, DisplayColumn>();

    public AuditQueryViewImpl(UserSchema schema, QuerySettings settings, @Nullable SimpleFilter filter)
    {
        super(schema, settings, filter);

        // Turn off the default QueryView details column.
        setShowDetailsColumn(false);
    }

    public void addDisplayColumn(int index, DisplayColumn dc)
    {
        _indexedColumns.put(index, dc);
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowBorders(true);

        if (!_columns.isEmpty())
        {
            if (getCustomView() == null)
            {
                for (DisplayColumn dc : view.getDataRegion().getDisplayColumns())
                {
                    if (dc instanceof DetailsColumn)
                        continue;
                    if (!_columns.contains(dc.getColumnInfo().getName().toLowerCase()))
                        dc.setVisible(false);
                }
            }
        }

        if (_filter != null)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (filter != null)
                filter.addAllClauses(_filter);
            else
                filter = _filter;
            view.getRenderContext().setBaseFilter(filter);
        }

        if (_sort != null)
        {
            Sort sort = view.getRenderContext().getBaseSort();
            if (sort == null)
                sort = new Sort();
            sort.insertSort(_sort);
            view.getRenderContext().setBaseSort(sort);
        }

        for (DisplayColumn dc : _displayColumns)
            view.getDataRegion().addDisplayColumn(dc);

        for (Map.Entry<Integer, DisplayColumn> entry : _indexedColumns.entrySet())
        {
            view.getDataRegion().addDisplayColumn(entry.getKey(), entry.getValue());
        }

        return view;
    }

    protected TableInfo createTable()
    {
        AuditLogTable table = new AuditLogTable(getSchema(), LogManager.get().getTinfoAuditLog(), getSettings().getQueryName());
        for (Map.Entry<String, AuditDisplayColumnFactory> entry : _displayColFactory.entrySet())
        {
            table.setDisplayColumnFactory(entry.getKey(), entry.getValue());
        }
        return table;
    }
}
