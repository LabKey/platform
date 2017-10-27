/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AnalyticsProviderItem;
import org.labkey.api.data.Container;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.VirtualFile;
import org.labkey.query.persist.QueryManager;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A custom view backed by an XML file in a module. Read-only.
 *
 * User: Dave
 * Date: Jan 9, 2009
 */
public class ModuleCustomView extends ModuleCustomViewInfo implements CustomView, EditableCustomView
{
    private final QueryDefinition _queryDef;

    public ModuleCustomView(QueryDefinition queryDef, ModuleCustomViewDef customViewDef)
    {
        super(customViewDef);
        _queryDef = queryDef;
    }

    public QueryDefinition getQueryDefinition()
    {
        return _queryDef;
    }

    /*
     * The file based custom view does not know its schema or query in general! Presumably,
     * the view got attached to the right query, so ask the query.
     */
    @Override @NotNull
    public String getSchemaName()
    {
        return _queryDef.getSchemaName();
    }

    @Override @NotNull
    public SchemaKey getSchemaPath()
    {
        return _queryDef.getSchemaPath();
    }

    @Override @NotNull
    public String getQueryName()
    {
        return _queryDef.getName();
    }

    public void setName(String name)
    {
        throw new UnsupportedOperationException("Can't set name on a module-based custom view!");
    }

    public void setQueryName(String queryName)
    {
        throw new UnsupportedOperationException("Can't set queryName on a module-based custom view!");
    }

    @Override
    public void setCanInherit(boolean f)
    {
        throw new UnsupportedOperationException("Module-based custom views cannot inherit");
    }

    public void setIsHidden(boolean f)
    {
        throw new UnsupportedOperationException("Module-based custom views cannot be set to hidden. " +
                "To suppress a module-based view, use Customize Folder to deactivate the module in this current folder.");
    }

    @Override
    public boolean isOverridable()
    {
        return _customViewDef.isOverridable();
    }

    public void setColumns(List<FieldKey> columns)
    {
        throw new UnsupportedOperationException("Can't set columns on a module-based custom view!");
    }

    public void setColumnProperties(List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty, String>>> list)
    {
        throw new UnsupportedOperationException("Can't set column properties on a module-based custom view!");
    }

    @Override
    public boolean canEdit(Container c, Errors errors)
    {
        if (errors != null && !isOverridable())
            errors.reject(null, "The module-based custom view '" + (getName() == null ? "<default>" : getName()) + "' is read-only and cannot be edited.");
        return isOverridable();
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        if (null != _customViewDef.getFilters())
        {
            for(Pair<String, String> filter : _customViewDef.getFilters())
            {
                url.addParameter(dataRegionName + "." + filter.first, filter.second);
            }
        }

        String sortParam = _customViewDef.getSortParamValue();

        if (null != sortParam)
            url.addParameter(dataRegionName + ".sort", sortParam);

        if (null != _customViewDef.getAnalyticsProviders())
        {
            for (AnalyticsProviderItem analyticsProviderItem : _customViewDef.getAnalyticsProviders())
                analyticsProviderItem.applyToURL(url, dataRegionName, analyticsProviderItem.getFieldKey());
        }
    }

    public void setFilterAndSortFromURL(ActionURL url, String dataRegionName)
    {
        throw new UnsupportedOperationException("Can't set the filter or sort of a module-based custom view!");
    }

    public void setFilterAndSort(String filter)
    {
        throw new UnsupportedOperationException("Can't set filter on a module-based custom view!");
    }

    public void save(User user, HttpServletRequest request) throws QueryException
    {
        throw new UnsupportedOperationException("Can't save a module-based custom view!");
    }

    public void delete(User user, HttpServletRequest request) throws QueryException
    {
        throw new UnsupportedOperationException("Can't delete a module-based custom view!");
    }

    public boolean serialize(VirtualFile dir)
    {
        // Do nothing -- shouldn't export ModuleCustomViews
        return false;
    }

    @Override
    public Collection<String> getDependents(User user)
    {
        return QueryManager.get().getQueryDependents(user, null, null, getSchemaPath(), Collections.singleton(getName()));
    }

    @Override
    @Nullable
    public CustomViewImpl getEditableViewInfo(User owner, boolean session)
    {
        if (!isOverridable())
        {
            return null;
        }

        CustomViewImpl cview;
        if (owner == null)
            cview = (CustomViewImpl)_queryDef.createSharedCustomView(getName());
        else
            cview = (CustomViewImpl)_queryDef.createCustomView(owner, getName());

        if (owner != null && session)
            cview.isSession(true);

        return cview;
    }

    @Override
    public List<String> getErrors()
    {
        return super.getErrors();
    }
}
