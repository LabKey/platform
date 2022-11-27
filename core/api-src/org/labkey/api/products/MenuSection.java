/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.products;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class MenuSection
{
    protected ViewContext _context;
    protected String _label;
    protected Integer _itemLimit;
    /**
     * A unique key identifying this MenuSection.
     * This is used by the client to match client configuration to the configuration supplied from LKS.
     */
    protected String _key;
    private List<MenuItem> _allItems;
    private String _productId;
    /** Used by the client to determine the first part of an app-relative URL. Defaults to the same as "key". */
    private String _sectionKey;

    public MenuSection(@NotNull ViewContext context, @NotNull String label, @NotNull String key, @Nullable Integer itemLimit, @Nullable String productId)
    {
        _context = context;
        _label = label;
        _key = key;
        _sectionKey = key;
        _itemLimit = itemLimit;
        _productId = productId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getKey()
    {
        return _key;
    }

    public void setKey(String key)
    {
        _key = key;
    }

    public int getTotalCount()
    {
        return ensureAllItems().size();
    }

    @JsonIgnore
    public ViewContext getContext()
    {
        return _context;
    }

    public void setContext(ViewContext context)
    {
        _context = context;
    }

    @JsonIgnore
    public Container getContainer()
    {
        return _context.getContainer();
    }

    @JsonIgnore
    public User getUser()
    {
        return _context.getUser();
    }

    public Integer getItemLimit()
    {
        return _itemLimit;
    }

    public void setItemLimit(Integer itemLimit)
    {
        _itemLimit = itemLimit;
    }

    public String getProductId()
    {
        return _productId;
    }

    public void setProductId(String productId)
    {
        _productId = productId;
    }

    // Might be handy if we implement linking deep into LabKey server from application, but also for URLResolver
    // There won't be one of these for each category, though, so default is null.
    public @Nullable ActionURL getUrl()
    {
        return null;
    }

    @JsonIgnore
    protected abstract @NotNull List<MenuItem> getAllItems();

    private @NotNull List<MenuItem> ensureAllItems()
    {
        if (_allItems == null)
            _allItems = getAllItems();
        return _allItems;
    }

    public List<MenuItem> getItems()
    {
        ensureAllItems().sort(Comparator.comparing(MenuItem::getOrderNum).thenComparing(MenuItem::getLabel, String.CASE_INSENSITIVE_ORDER));
        if (_itemLimit != null && _itemLimit < _allItems.size())
            return _allItems.subList(0, _itemLimit);
        else
            return _allItems;
    }

    protected TableInfo getTableInfo(UserSchema schema, String queryName)
    {
        QuerySettings settings = schema.getSettings(getContext(), QueryView.DATAREGIONNAME_DEFAULT, queryName);
        QueryDefinition def = settings.getQueryDef(schema);
        return def.getTable(new ArrayList<>(), true);
    }

    protected String getLabel(UserSchema schema, String queryName)
    {
        TableInfo tableInfo = getTableInfo(schema, queryName);
        String label = tableInfo.getTitle();
        if (label == null)
            label = tableInfo.getName();
        return label;
    }

    public String getSectionKey()
    {
        return _sectionKey;
    }

    public void setSectionKey(String sectionKey)
    {
        _sectionKey = sectionKey;
    }
}
