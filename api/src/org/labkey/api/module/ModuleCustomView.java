/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.data.Container;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
* User: Dave
* Date: Jan 9, 2009
* Time: 4:37:30 PM
*/
public class ModuleCustomView implements CustomView
{
    private QueryDefinition _queryDef;
    private ModuleCustomViewDef _customViewDef;

    public ModuleCustomView(QueryDefinition queryDef, ModuleCustomViewDef customViewDef)
    {
        _queryDef = queryDef;
        _customViewDef = customViewDef;
    }

    public QueryDefinition getQueryDefinition()
    {
        return _queryDef;
    }

    public String getName()
    {
        return _customViewDef.getName();
    }

    public User getOwner()
    {
        //module-based reports have no owner
        return null;
    }

    public Container getContainer()
    {
        //module-based reports have no explicit container
        return null;
    }

    public boolean canInherit()
    {
        return false;
    }

    public void setCanInherit(boolean f)
    {
        throw new UnsupportedOperationException("Module-based custom views cannot inherit");
    }

    public boolean isHidden()
    {
        return _customViewDef.isHidden();
    }

    public void setIsHidden(boolean f)
    {
        throw new UnsupportedOperationException("Module-based custom views cannot be set to hidden. " +
                "To suppress a module-based view, use Customize Folder to deactivate the module in this current folder.");
    }

    public boolean isEditable()
    {
        //module custom views are not updatable
        return false;
    }

    public String getCustomIconUrl()
    {
        return _customViewDef.getCustomIconUrl();
    }

    public List<FieldKey> getColumns()
    {
        List<FieldKey> ret = new ArrayList<FieldKey>();
        for(Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>> entry : getColumnProperties())
        {
            ret.add(entry.getKey());
        }
        return ret;
    }

    public List<Map.Entry<FieldKey,Map<CustomView.ColumnProperty,String>>> getColumnProperties()
    {
        return _customViewDef.getColList();
    }

    public void setColumns(List<FieldKey> columns)
    {
        throw new UnsupportedOperationException("Can't set columns on a module-based custom view!");
    }

    public void setColumnProperties(List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> list)
    {
        throw new UnsupportedOperationException("Can't set column properties on a module-based custom view!");
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        if(null != _customViewDef.getFilters())
        {
            for(Pair<String,String> filter : _customViewDef.getFilters())
            {
                url.addParameter(dataRegionName + "." + filter.first, filter.second);
            }
        }

        String sortParam = buildSortParamValue();
        if(null != sortParam)
            url.addParameter(dataRegionName + ".sort", sortParam);
    }

    protected String buildSortParamValue()
    {
        if(null == _customViewDef.getSorts())
            return null;

        StringBuilder sortParam = new StringBuilder();
        String sep = "";
        for(String sort : _customViewDef.getSorts())
        {
            sortParam.append(sep);
            sortParam.append(sort);
            sep = ",";
        }
        return sortParam.toString();
    }

    public void setFilterAndSortFromURL(ActionURL url, String dataRegionName)
    {
        throw new UnsupportedOperationException("Can't set the filter or sort of a module-based custom view!");
    }

    public String getFilter()
    {
        if(null == _customViewDef.getFilters())
            return null;

        StringBuilder ret = new StringBuilder();
        for(Pair<String,String> filter : _customViewDef.getFilters())
        {
            ret.append(filter.first);
            ret.append("=");
            ret.append(filter.second);
        }

        return ret.toString();
    }

    public void setFilter(String filter)
    {
        throw new UnsupportedOperationException("Can't set filter on a module-based custom view!");
    }

    public String getContainerFilterName()
    {
        if(null == _customViewDef.getFilters())
            return null;

        for(Pair<String,String> filter : _customViewDef.getFilters())
        {
            if(filter.first.startsWith("containerFilterName~"))
                return filter.second;
        }
        return null;
    }

    public boolean hasFilterOrSort()
    {
        return (null != _customViewDef.getFilters() || null != _customViewDef.getSorts());
    }

    public void save(User user, HttpServletRequest request) throws QueryException
    {
        throw new UnsupportedOperationException("Can't save a module-based custom view!");
    }

    public void delete(User user, HttpServletRequest request) throws QueryException
    {
        throw new UnsupportedOperationException("Can't delete a module-based custom view!");
    }

}