/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**

 */
abstract public class AbstractQueryNavItem extends AbstractNavItem
{
    private String _name;
    private String _label;
    private String _schema;
    private String _query;
    private boolean _visible = true;

    public AbstractQueryNavItem(DataProvider provider, String schema, String query, LaboratoryService.NavItemCategory itemType, String reportCategory, String label)
    {
        super(provider, itemType, reportCategory);
        _schema = schema;
        _query = query;
        _name = query;
        _label = label;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getLabel()
    {
        return _label;
    }

    @Override
    public String getRendererName()
    {
        return "linkWithLabel";
    }

    @Override
    public boolean getDefaultVisibility(Container c, User u)
    {
        return _visible;
    }

    public String getSchema()
    {
        return _schema;
    }

    public String getQuery()
    {
        return _query;
    }

    public void setVisible(boolean visible)
    {
        _visible = visible;
    }

    protected TableInfo getTableInfo(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, getTargetContainer(c), _schema);
        if (us == null)
        {
            _log.error("Unable to find schema: " + _schema + " in container: " + getTargetContainer(c).getPath());
            return null;
        }

        return us.getTable(_query);
    }

    protected ActionURL getActionURL(Container c, User u)
    {
        return QueryService.get().urlFor(u, getTargetContainer(c), QueryAction.executeQuery, getSchema(), getQuery());
    }

    abstract protected String getItemText(Container c, User u);

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);

        json.put("schemaName", _schema);
        json.put("queryName", _query);

        json.put("urlConfig", getUrlObject(getActionURL(c, u)));
        json.put("itemText", getItemText(c, u));

        return json;
    }
}
