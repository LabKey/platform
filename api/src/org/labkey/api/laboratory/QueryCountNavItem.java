/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 12:27 PM
 */
public class QueryCountNavItem extends AbstractQueryNavItem implements SummaryNavItem
{
    private SimpleFilter _filter = null;

    public QueryCountNavItem(DataProvider provider, String schema, String query, LaboratoryService.NavItemCategory itemType, String reportCategory, String label)
    {
        super(provider, schema, query, itemType, reportCategory, label);
    }

    @Override
    public Long getRowCount(Container c, User u)
    {
        TableInfo ti = getTableInfo(c, u);
        if (ti == null)
            return new Long(0);

        SimpleFilter filter = getFilter(c, ti);
        TableSelector ts = new TableSelector(ti, ti.getPkColumns(), filter, null);
        return ts.getRowCount();
    }

    protected SimpleFilter getFilter(Container c, TableInfo ti)
    {
        SimpleFilter filter = new SimpleFilter();

        if (ti.getColumn("container") != null && !(ti.supportsContainerFilter() && ContainerFilter.CURRENT.equals(ti.getContainerFilter())))
            filter.addClause(ContainerFilter.CURRENT.createFilterClause(ti.getSchema(), FieldKey.fromString("container"), c));

        if (_filter != null)
        {
            for (SimpleFilter.FilterClause clause : _filter.getClauses())
                filter.addCondition(clause);
        }

        return filter;
    }

    @Override
    protected String getItemText(Container c, User u)
    {
        try
        {
            Long total = getRowCount(c, u);
            return total.toString();
        }
        catch (Exception e)
        {
            _log.error("Error calculating rowcount for table " + getSchema() + "." + getQuery(), e);
            return "0";
        }
    }

    @Override
    protected ActionURL getActionURL(Container c, User u)
    {
        ActionURL url = QueryService.get().urlFor(u, getTargetContainer(c), QueryAction.executeQuery, getSchema(), getQuery());
        if (_filter != null)
            _filter.applyToURL(url, "query");

        return url;
    }

    public void setFilter(SimpleFilter filter)
    {
        _filter = filter;
    }
}
