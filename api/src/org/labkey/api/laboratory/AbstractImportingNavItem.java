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

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

/**
 * User: bimber
 * Date: 11/21/12
 * Time: 5:08 PM
 */
abstract public class AbstractImportingNavItem extends AbstractNavItem implements ImportingNavItem
{
    private String _name;
    private String _label;
    private String _category;
    private DataProvider _provider;

    public AbstractImportingNavItem(DataProvider provider, String name, String label, String category)
    {
        _provider = provider;
        _name = name;
        _label = label;
        _category = category;
    }

    @Override
    public DataProvider getDataProvider()
    {
        return _provider;
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
    public String getCategory()
    {
        return _category;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);

        ret.put("importIntoWorkbooks", isImportIntoWorkbooks(c, u));

        ret.put("importUrl", getUrlObject(getImportUrl(c, u)));
        ret.put("searchUrl", getUrlObject(getSearchUrl(c, u)));
        ret.put("browseUrl", getUrlObject(getBrowseUrl(c, u)));
        ret.put("browseDefaultView", getDefaultViewName(c, getPropertyManagerKey()));

        return ret;
    }

    @Override
    public String getRendererName()
    {
        return "importingNavItem";
    }
}
