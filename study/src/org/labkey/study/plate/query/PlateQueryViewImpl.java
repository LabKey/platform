/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study.plate.query;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.data.*;
import org.labkey.api.study.PlateQueryView;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Nov 2, 2006
 * Time: 10:50:49 AM
 */
public class PlateQueryViewImpl extends PlateQueryView
{
    private SimpleFilter _filter;
    private Sort _sort;
    private List<ActionButton> _buttons;
    private Map<String, String> _hiddenFormFields;

    public PlateQueryViewImpl(ViewContext context, QuerySettings settings, SimpleFilter filter)
    {
        super(new PlateSchema(context.getUser(), context.getContainer()), settings);
        _filter = filter;
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
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
        if (_buttons != null)
        {
            ButtonBar bbar = view.getDataRegion().getButtonBar(DataRegion.MODE_GRID);
            for (ActionButton button : _buttons)
                bbar.add(button);
            view.getDataRegion().setButtonBar(bbar);
        }
        else
            view.getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        return view;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        DataRegion region = super.createDataRegion();
        region.setShowRecordSelectors(true);
        region.setRecordSelectorValueColumns("RowId");

        if (_hiddenFormFields != null)
        {
            for (Map.Entry<String, String> field : _hiddenFormFields.entrySet())
                region.addHiddenFormField(field.getKey(), field.getValue());
        }
        return region;
    }

    public void addHiddenFormField(String key, String value)
    {
        if (_hiddenFormFields == null)
            _hiddenFormFields = new HashMap<>();
        _hiddenFormFields.put(key, value);
    }

    public void setButtons(List<ActionButton> buttons)
    {
        _buttons = buttons;
    }

    public boolean hasRecords() throws SQLException, IOException
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        ResultSet rs = null;
        try
        {
            rs = rgn.getResultSet(view.getRenderContext());
            return rs.next();
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {}
        }
    }

    public void setSort(Sort sort)
    {
        _sort = sort;
    }
}
