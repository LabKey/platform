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

package org.labkey.audit.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditDisplayColumnFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.DataView;
import org.labkey.audit.model.LogManager;

import java.io.PrintWriter;
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

    public AuditQueryViewImpl(UserSchema schema, QuerySettings settings, SimpleFilter filter)
    {
        super(schema, settings, filter);
        getSettings().setAllowChooseQuery(false);
    }

    public void addDisplayColumn(int index, DisplayColumn dc)
    {
        _indexedColumns.put(index, dc);
    }

    protected DataView createDataView()
    {
        setShowDetailsColumn(false);

        DataView view = super.createDataView();

        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowColumnSeparators(true);

        if (!_columns.isEmpty())
        {
            if (getCustomView() == null)
            {
                for (DisplayColumn dc : view.getDataRegion().getDisplayColumns())
                {
                    if (!_columns.contains(dc.getName().toLowerCase()))
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
        if (_sort != null && view.getRenderContext().getBaseSort() == null)
            view.getRenderContext().setBaseSort(_sort);

        for (DisplayColumn dc : _displayColumns)
            view.getDataRegion().addDisplayColumn(dc);

        for (Map.Entry<Integer, DisplayColumn> entry : _indexedColumns.entrySet())
        {
            view.getDataRegion().addDisplayColumn(entry.getKey(), entry.getValue());
        }

        setupView(view);

        return view;
    }

    private void setupView(DataView view)
    {
        AuditLogService.AuditViewFactory factory = AuditLogService.get().getAuditViewFactory(getSettings().getQueryName());
        if (factory != null)
        {
            factory.setupView(view);
        }
    }

    protected void renderDataRegion(PrintWriter out) throws Exception
    {
        if (_title != null)
            out.print("<br/>" + _title + "<br/>");
        super.renderDataRegion(out);
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
