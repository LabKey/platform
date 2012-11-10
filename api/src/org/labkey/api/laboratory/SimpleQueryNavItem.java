/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 12:27 PM
 */
public class SimpleQueryNavItem extends AbstractNavItem
{
    private String _schema;
    private String _query;
    private String _label;
    private String _category;
    private DataProvider _dataProvider;

    public SimpleQueryNavItem(DataProvider provider, String schema, String query, String category)
    {
        _schema = schema;
        _query = query;
        _label = query;
        _category = category;
        _dataProvider = provider;
    }

    public SimpleQueryNavItem(DataProvider provider, String schema, String query, String category, String label)
    {
        _schema = schema;
        _query = query;
        _category = category;
        _dataProvider = provider;
        _label = label;
    }

    public String getName()
    {
        return _query;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getCategory()
    {
        return _category;
    }

    public String getRendererName()
    {
        return "navItemRenderer";
    }

    public boolean isImportIntoWorkbooks()
    {
        return true;
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return true;
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        try
        {
            return QueryService.get().urlFor(u, c, QueryAction.importData, _schema, _query);
        }
        catch (QueryParseException e)
        {
            return null;
        }
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, _schema, _query);
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        try
        {
            return QueryService.get().urlFor(u, c, QueryAction.executeQuery, _schema, _query);
        }
        catch (QueryParseException e)
        {
            return null;
        }
    }

    public DataProvider getDataProvider()
    {
        return _dataProvider;
    }
}
