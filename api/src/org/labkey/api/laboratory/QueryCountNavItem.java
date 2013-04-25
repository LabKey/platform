/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 12:27 PM
 */
public class QueryCountNavItem extends SingleNavItem
{
    private String _schema;
    private String _query;

    public QueryCountNavItem(DataProvider provider, String schema, String query, String category, String label)
    {
        super(provider, label, null, (DetailsURL)null, category);
        _schema = schema;
        _query = query;
    }

    public String getName()
    {
        return _query;
    }

    public String getRendererName()
    {
        return "singleItemRenderer";
    }

    private TableInfo getTableInfo(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, _schema);
        TableInfo ti = us.getTable(_query);
        return ti;
    }

    private Long getRowCount(Container c, User u)
    {
        TableInfo ti = getTableInfo(c, u);
        SimpleFilter filter = null;
        if (ti.getColumn("container") != null)
            new SimpleFilter(FieldKey.fromString("container"), c.getEntityId());

        TableSelector ts = new TableSelector(ti, ti.getPkColumns(), filter, null);
        return ts.getRowCount();
    }

    protected ActionURL getActionURL(Container c, User u)
    {
        return QueryService.get().urlFor(u, c, QueryAction.executeQuery, _schema, _query);
    }

    public JSONObject toJSON(Container c, User u)
    {
        Long total = getRowCount(c, u);
        _itemText = total.toString();

        return super.toJSON(c, u);
    }
}
