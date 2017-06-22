/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.data.views;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: klum
 * Date: Apr 2, 2012
 */
public class DataViewService
{
    private final Map<DataViewProvider.Type, DataViewProvider> _providers = new ConcurrentHashMap<>();

    private static final Logger _log = Logger.getLogger(DataViewService.class);
    private static final DataViewService _instance = new DataViewService();
    private static final Map<String, Boolean> _providerInitialized = new HashMap<>();

    public static DataViewService get()
    {
        return _instance;
    }

    private DataViewService(){}

    public void registerProvider(DataViewProvider.Type type, DataViewProvider provider)
    {
        if (_providers.containsKey(type))
            throw new IllegalArgumentException("A provider for type: " + type.getName() + " has already been registered");

        _providers.put(type, provider);
    }

    public List<DataViewProvider.Type> getDataTypes(Container container, User user)
    {
        List<DataViewProvider.Type> types = new ArrayList<>();

        for (Map.Entry<DataViewProvider.Type, DataViewProvider> entry : _providers.entrySet())
        {
            if (entry.getValue().isVisible(container, user))
                types.add(entry.getKey());
        }
        return types;
    }

    @Nullable
    public DataViewProvider.Type getDataTypeByName(String typeName)
    {
        for (DataViewProvider.Type type : _providers.keySet())
        {
            if (type.getName().equals(typeName))
                return type;
        }
        return null;
    }

    /**
     * Gets the data views for all visible data types
     */
    public List<DataViewInfo> getViews(ViewContext context) throws Exception
    {
        return getViews(context, new ArrayList<>(_providers.keySet()));
    }

    /**
     * Gets the data views for all the specified data types
     */
    public List<DataViewInfo> getViews(ViewContext context, List<DataViewProvider.Type> types) throws Exception
    {
        List<DataViewInfo> views = new ArrayList<>();

        for (DataViewProvider.Type type : types)
        {
            if (_providers.containsKey(type))
            {
                DataViewProvider provider = _providers.get(type);
                initializeProvider(provider, context);

                if (provider.isVisible(context.getContainer(), context.getUser()))
                    views.addAll(provider.getViews(context));
            }
            else
                throw new IllegalStateException("Provider type: " + type.getName() + " not found.");
        }
        return views;
    }

    private void initializeProvider(DataViewProvider provider, ContainerUser context)
    {
        if (provider != null)
        {
            try {
                String key = getCacheKey(context.getContainer(), provider.getType());

                if (!_providerInitialized.containsKey(key))
                {
                    provider.initialize(context);
                    _providerInitialized.put(key, true);
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private String getCacheKey(Container c, DataViewProvider.Type type)
    {
        return c.getId() + "-" + type.getName();
    }

    public DataViewProvider getProvider(DataViewProvider.Type type, ContainerUser context)
    {
        if (_providers.containsKey(type))
        {
            DataViewProvider provider = _providers.get(type);
            initializeProvider(provider, context);

            return provider;
        }
        else
            throw new IllegalStateException("Provider type: " + type.getName() + " not found.");
    }

    public static final int DEFAULT_CATEGORY_DISPLAY_ORDER = 1000;

    public JSONArray toJSON(Container container, User user, List<DataViewInfo> views)
    {
        JSONArray jsonViews = new JSONArray();

        for (DataViewInfo info : views)
            jsonViews.put(toJSON(container, user, info));

        return jsonViews;
    }

    public JSONObject toJSON(Container container, User user, DataViewInfo info)
    {
        JSONObject o = new JSONObject();

        o.put("id", info.getId());
        if (info instanceof DefaultViewInfo && ((DefaultViewInfo) info).getReportId() != null)
        {
            o.put("reportId", ((DefaultViewInfo) info).getReportId());
        }
        else
        {
            o.put("reportId", (String) null);
        }

        o.put("dataType", info.getDataType().getName());
        o.put("name", info.getName());
        o.put("container", info.getContainer().getPath());

        if (info.getType() != null)
            o.put("type", info.getType());
        if (info.getDescription() != null)
            o.put("description", info.getDescription());

        if (info.getSchemaName() != null)
            o.put("schemaName", info.getSchemaName());
        if (info.getQueryName() != null)
            o.put("queryName", info.getQueryName());

        if (info.getDefaultIconCls() != null)
            o.put("defaultIconCls", info.getDefaultIconCls());
        if (info.getDefaultThumbnailUrl() != null)
            o.put("defaultThumbnailUrl", info.getDefaultThumbnailUrl());
        if (info.getIconUrl() != null)
            o.put("icon", info.getIconUrl().getLocalURIString());
        if (info.getIconCls() != null)
            o.put("iconCls", info.getIconCls() + " icon-content");

        ViewCategory vc = info.getCategory();
        if (vc == null)
        {
            // create a default view category
            vc = new ViewCategory();
            vc.setLabel("Uncategorized");
            vc.setDisplayOrder(DEFAULT_CATEGORY_DISPLAY_ORDER);
        }
        o.put("category", vc.toJSON(user));

        o.put("visible", info.isVisible());
        o.put("showInDashboard", info.showInDashboard());
        o.put("shared", info.isShared());
        o.put("readOnly", info.isReadOnly());

        if (info.getAccess() != null)
            o.put("access", createAccessObject(info.getAccess(), info.getAccessUrl()));

        if (info.getCreatedBy() != null)
        {
            o.put("createdBy", info.getCreatedBy().getDisplayName(user));
            // temporary, refactor in 12.1
            o.put("createdByUserId", info.getCreatedBy().getUserId());
        }
        if (info.getModifiedBy() != null)
            o.put("modifiedBy", info.getModifiedBy().getDisplayName(user));
        if (info.getAuthor() != null)
            o.put("author", info.getAuthor().getDisplayName(user));

        if (info.getCreated() != null)
            o.put("created",  info.getCreated());
        if (info.getModified() != null)
            o.put("modified", info.getModified());
        if (info.getContentModified() != null)
            o.put("contentModified", info.getContentModified());

        if (info.getRunUrl() != null)
            o.put("runUrl", info.getRunUrl().getLocalURIString());
        if (info.getRunTarget() != null)
            o.put("runTarget", info.getRunTarget());
        if (info.getThumbnailUrl() != null)
            o.put("thumbnail", info.getThumbnailUrl().getLocalURIString());

        if (info.getDetailsUrl() != null)
            o.put("detailsUrl", info.getDetailsUrl().getLocalURIString());

        o.put("allowCustomThumbnail", info.isAllowCustomThumbnail());

        // tags
        for (Pair<DomainProperty, Object> tag : info.getTags())
        {
            DomainProperty dp = tag.getKey();

            if (DataViewProvider.EditInfo.Property.author.name().equals(dp.getName()))
            {
                User u = null;
                if (tag.getValue() instanceof Number)
                    u = UserManager.getUser(((Number)tag.getValue()).intValue());

                if (u != null)
                    o.put(dp.getName(), createUserObject(u, user));
                else
                    o.put(dp.getName(), String.valueOf(tag.getValue()));
            }
            else
            {
                Object value = tag.getValue();

                if (value instanceof Date)
                    o.put(dp.getName(), value);
                else
                    o.put(dp.getName(), String.valueOf(tag.getValue()));
            }
        }
        return o;
    }

    private JSONObject createUserObject(User user, User currentUser)
    {
        JSONObject json = new JSONObject();

        json.put("userId", user != null ? user.getUserId() : "");
        json.put("displayName", user != null ? user.getDisplayName(currentUser) : "");

        return json;
    }

    private JSONObject createAccessObject(String access, ActionURL accessUrl)
    {
        JSONObject json = new JSONObject();

        json.put("label", access);
        if (accessUrl != null)
            json.put("url", accessUrl.getLocalURIString());

        return json;
    }

    public JSONObject toJSON(Container container, User user, DataViewProvider.EditInfo info)
    {
        JSONObject o = new JSONObject();

        JSONObject props = new JSONObject();
        for (String propName : info.getEditableProperties(container, user))
            props.put(propName, true);
        o.put("props", props);

        JSONObject actions = new JSONObject();
        for (DataViewProvider.EditInfo.Actions action : info.getAllowableActions(container, user))
            actions.put(action.name(), true);
        o.put("actions", actions);

        return o;
    }
}
