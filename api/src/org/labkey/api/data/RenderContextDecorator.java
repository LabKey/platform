/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Sep 7, 2010
 * Time: 9:56:18 AM
 */
public class RenderContextDecorator extends RenderContext
{
    private final RenderContext _ctx;

    public RenderContextDecorator(RenderContext ctx)
    {
        _ctx = ctx;
    }

    @Override
    public void setViewContext(ViewContext context)
    {
        _ctx.setViewContext(context);
    }

    @Override
    public
    @Nullable
    Errors getErrors()
    {
        return _ctx.getErrors();
    }

    @Override
    public void setErrors(@Nullable Errors errors)
    {
        _ctx.setErrors(errors);
    }

    @Override
    public TableViewForm getForm()
    {
        return _ctx.getForm();
    }

    @Override
    public void setForm(TableViewForm form)
    {
        _ctx.setForm(form);
    }

    @Override
    public DataRegion getCurrentRegion()
    {
        return _ctx.getCurrentRegion();
    }

    @Override
    public void setCurrentRegion(DataRegion currentRegion)
    {
        _ctx.setCurrentRegion(currentRegion);
    }

    @Override
    public String getSelectionKey()
    {
        return _ctx.getSelectionKey();
    }

    @Override
    public Filter getBaseFilter()
    {
        return _ctx.getBaseFilter();
    }

    @Override
    public void setBaseFilter(Filter filter)
    {
        _ctx.setBaseFilter(filter);
    }

    @Override
    public Sort getBaseSort()
    {
        return _ctx.getBaseSort();
    }

    @Override
    public void setBaseSort(Sort sort)
    {
        _ctx.setBaseSort(sort);
    }

    @Override
    public List<AnalyticsProviderItem> getBaseSummaryStatsProviders()
    {
        return _ctx.getBaseSummaryStatsProviders();
    }

    @Override
    public ActionURL getSortFilterURLHelper()
    {
        return _ctx.getSortFilterURLHelper();
    }

    @Override
    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return _ctx.getFieldMap();
    }

    @Override
    public Results getResultSet(Map<FieldKey, ColumnInfo> fieldMap, List<DisplayColumn> displayColumns, TableInfo tinfo, QuerySettings settings, Map<String, Object> parameters, int maxRows, long offset, String name, boolean async)
            throws SQLException, IOException
    {
        return _ctx.getResultSet(fieldMap, displayColumns, tinfo, settings, parameters, maxRows, offset, name, async);
    }

    @Override
    public Map<String, List<Aggregate.Result>> getAggregates(List<DisplayColumn> displayColumns, TableInfo tinfo, QuerySettings settings, String dataRegionName,
                                                             List<Aggregate> aggregatesIn, Map<String, Object> parameters, boolean async)
            throws IOException
    {
        return _ctx.getAggregates(displayColumns, tinfo, settings, dataRegionName, aggregatesIn, parameters, async);
    }

    @Override
    public Sort buildSort(TableInfo tinfo, ActionURL url, String name)
    {
        return _ctx.buildSort(tinfo, url, name);
    }

    @Override
    public SimpleFilter buildFilter(TableInfo tinfo, ActionURL url, String name, int maxRows, long offset, Sort sort)
    {
        return _ctx.buildFilter(tinfo, url, name, maxRows, offset, sort);
    }

    @Override
    public SimpleFilter buildFilter(TableInfo tinfo, List<ColumnInfo> displayColumns, ActionURL url, String name, int maxRows, long offset, Sort sort)
    {
        return _ctx.buildFilter(tinfo, displayColumns, url, name, maxRows, offset, sort);
    }

    @Override
    public void buildSelectedFilter(SimpleFilter filter, TableInfo tinfo, boolean inverted)
    {
        _ctx.buildSelectedFilter(filter, tinfo, inverted);
    }

    @Override
    public Results selectForDisplay(TableInfo table, Collection<ColumnInfo> columns, Map<String, Object> parameters, SimpleFilter filter, Sort sort, int maxRows, long offset, boolean async)
            throws SQLException, IOException
    {
        return _ctx.selectForDisplay(table, columns, parameters, filter, sort, maxRows, offset, async);
    }

    @Override
    public boolean getCache()
    {
        return _ctx.getCache();
    }

    @Override
    public void setCache(boolean cache)
    {
        _ctx.setCache(cache);
    }

    @Override
    public Map<String, Object> getRow()
    {
        return _ctx.getRow();
    }

    @Override
    public void setRow(Map<String, Object> row)
    {
        _ctx.setRow(row);
    }

    @Override
    public int getMode()
    {
        return _ctx.getMode();
    }

    @Override
    public void setMode(int mode)
    {
        _ctx.setMode(mode);
    }

    @Override
    public boolean isEmpty()
    {
        return _ctx.isEmpty();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return _ctx.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        return _ctx.containsValue(value);
    }

    @Override
    public Collection values()
    {
        return _ctx.values();
    }

    @Override
    public Set entrySet()
    {
        return _ctx.entrySet();
    }

    @Override
    public Set keySet()
    {
        return _ctx.keySet();
    }

    @Override
    public Object get(Object key)
    {
        return _ctx.get(key);
    }

    @Override
    public String getContainerPath()
    {
        return _ctx.getContainerPath();
    }

    @Override
    public String getContextPath()
    {
        return _ctx.getContextPath();
    }

    @Override
    public Container getContainer()
    {
        return _ctx.getContainer();
    }

    @Override
    public void setContainer(Container c)
    {
        _ctx.setContainer(c);
    }

    @Override
    public HttpServletRequest getRequest()
    {
        return _ctx.getRequest();
    }

    @Override
    public void setRequest(HttpServletRequest request)
    {
        _ctx.setRequest(request);
    }

    @Override
    public ViewContext getViewContext()
    {
        return _ctx.getViewContext();
    }

    @Override
    public boolean isUseContainerFilter()
    {
        return _ctx.isUseContainerFilter();
    }

    @Override
    public void setUseContainerFilter(boolean useContainerFilter)
    {
        _ctx.setUseContainerFilter(useContainerFilter);
    }

    @Override
    public Set<FieldKey> getIgnoredFilterColumns()
    {
        return _ctx.getIgnoredFilterColumns();
    }

    @Override
    public String getErrors(String paramName)
    {
        return _ctx.getErrors(paramName);
    }

    @Override
    public String getErrors(ColumnInfo column)
    {
        return _ctx.getErrors(column);
    }

    @Override
    public List<String> getRecordSelectorValueColumns()
    {
        return _ctx.getRecordSelectorValueColumns();
    }

    @Override
    public Set<String> getAllSelected()
    {
        return _ctx.getAllSelected();
    }

    @Override
    public void setAllSelected(Set<String> selected)
    {
        _ctx.setAllSelected(selected);
    }

    @Override
    public void setView(CustomView view)
    {
        _ctx.setView(view);
    }

    @Override
    public CustomView getView()
    {
        return _ctx.getView();
    }

    @Override
    public Object put(String key, Object value)
    {
        return _ctx.put(key, value);
    }

    @Override
    public int size()
    {
        return _ctx.size();
    }

    @Override
    public Object remove(Object key)
    {
        return _ctx.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m)
    {
        _ctx.putAll(m);
    }

    @Override
    public void clear()
    {
        _ctx.clear();
    }

    @Override
    public boolean equals(Object o)
    {
        return _ctx.equals(o);
    }

    @Override
    public int hashCode()
    {
        return _ctx.hashCode();
    }

    @Override
    public String toString()
    {
        return _ctx.toString();
    }
}
