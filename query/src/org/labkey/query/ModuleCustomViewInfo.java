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
package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: Jun 21, 2010
 * Time: 1:17:33 PM
 */
public class ModuleCustomViewInfo implements CustomViewInfo
{
    protected ModuleCustomViewDef _customViewDef;

    public ModuleCustomViewInfo(ModuleCustomViewDef customViewDef)
    {
        _customViewDef = customViewDef;
    }

    @Override
    public String getName()
    {
        return _customViewDef.getName();
    }

    @Override
    public String getLabel()
    {
        return _customViewDef.getLabel();
    }

    @Override
    public User getOwner()
    {
        //module-based reports have no owner
        return null;
    }

    @Override
    public boolean isShared()
    {
        return true;
    }

    @Override
    public boolean isOverridable()
    {
        return _customViewDef.isOverridable();
    }

    @Override
    public User getCreatedBy()
    {
        return null;
    }

    @Override
    public Date getCreated()
    {
        return _customViewDef.getLastModified();
    }

    @Override
    public User getModifiedBy()
    {
        return null;
    }

    @Override
    public Date getModified()
    {
        return _customViewDef.getLastModified();
    }

    @Override
    public String getSchemaName()
    {
        return _customViewDef.getSchema();
    }

    @Override
    public SchemaKey getSchemaPath()
    {
        return SchemaKey.fromString(_customViewDef.getSchema());
    }

    @Override
    public String getQueryName()
    {
        return _customViewDef.getQuery();
    }

    @Override
    public Container getContainer()
    {
        //module-based reports have no explicit container
        return null;
    }

    @Override
    public String getEntityId()
    {
        return null;
    }

    @Override
    public boolean canInherit()
    {
        return false;
    }

    @Override
    public boolean isHidden()
    {
        return _customViewDef.isHidden();
    }

    public boolean isShowInDataViews()
    {
        return _customViewDef.isShowInDataViews();
    }

    public String getCategory()
    {
        return _customViewDef.getCategory();
    }

    @Override
    public boolean isEditable()
    {
        //module custom views are not updatable, unless the XML has flagged this as true
        return isOverridable();
    }

    @Override
    public boolean isDeletable()
    {
        return false;
    }

    @Override
    public boolean isRevertable()
    {
        return false;
    }

    @Override
    public boolean isSession()
    {
        // module custom views are never in session
        return false;
    }

    @Override
    public String getCustomIconUrl()
    {
        return _customViewDef.getCustomIconUrl();
    }

    @Override
    public String getCustomIconCls()
    {
        return _customViewDef.getCustomIconCls();
    }

    @NotNull
    @Override
    public List<FieldKey> getColumns()
    {
        List<FieldKey> ret = new ArrayList<>();
        for(Map.Entry<FieldKey, Map<CustomView.ColumnProperty,String>> entry : getColumnProperties())
        {
            ret.add(entry.getKey());
        }
        return ret;
    }

    @NotNull
    @Override
    public List<Map.Entry<FieldKey, Map<CustomView.ColumnProperty,String>>> getColumnProperties()
    {
        return _customViewDef.getColList();
    }

    @Override
    public String getFilterAndSort()
    {
        return _customViewDef.getFilterAndSortString();
    }

    @Override
    public String getContainerFilterName()
    {
        if (null != _customViewDef.getContainerFilterName())
            return _customViewDef.getContainerFilterName();

        if (null == _customViewDef.getFilters())
            return null;

        for(Pair<String, String> filter : _customViewDef.getFilters())
        {
            if (filter.first.startsWith("containerFilterName~"))
                return filter.second;
        }

        return null;
    }


    @Override
    public boolean hasFilterOrSort()
    {
        return (null != _customViewDef.getFilters()
            || null != _customViewDef.getSorts()
            || null != _customViewDef.getAnalyticsProviders()
        );
    }

    public List<String> getErrors()
    {
        return _customViewDef.getErrors();
    }

}
