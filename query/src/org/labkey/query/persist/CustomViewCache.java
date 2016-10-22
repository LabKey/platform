/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.query.persist;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveArrayListValuedMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by klum on 10/16/2015.
 */
public class CustomViewCache
{
    private static final Cache<Container, CustomViewCollections> CUSTOM_VIEW_DB_CACHE = CacheManager.getBlockingCache(
            CacheManager.UNLIMITED, CacheManager.DAY, "Database Custom View Cache",
            (c, argument) -> new CustomViewCollections(c)
    );

    private static class CustomViewCollections
    {
        // schemaName, queryName, viewName keys
        private final Map<String, MultiValuedMap<String, CstmView>> _customViews;
        private final Map<String, MultiValuedMap<String, CstmView>> _inheritableCustomViews;
        private final Map<Integer, CstmView> _rowIdMap;
        private final Map<String, CstmView> _entityIdMap;

        private CustomViewCollections(Container c)
        {
            Map<String, CaseInsensitiveArrayListValuedMap<CstmView>> customViews = new CaseInsensitiveHashMap<>();
            Map<String, CaseInsensitiveArrayListValuedMap<CstmView>> inheritableCustomViews = new CaseInsensitiveHashMap<>();
            Map<Integer, CstmView> rowIdMap = new HashMap<>();
            Map<String, CstmView> entityIdMap = new HashMap<>();

            new TableSelector(QueryManager.get().getTableInfoCustomView(), SimpleFilter.createContainerFilter(c), null).forEach(cstmView -> {

                MultiValuedMap<String, CstmView> viewMap = ensureViewMultiMap(customViews, cstmView.getSchema());

                viewMap.put(cstmView.getQueryName(), cstmView);
                rowIdMap.put(cstmView.getCustomViewId(), cstmView);
                entityIdMap.put(cstmView.getEntityId(), cstmView);
                if ((cstmView.getFlags() & QueryManager.FLAG_INHERITABLE) != 0)
                {
                    MultiValuedMap<String, CstmView> inheritableViewMap = ensureViewMultiMap(inheritableCustomViews, cstmView.getSchema());
                    inheritableViewMap.put(cstmView.getQueryName(), cstmView);
                }

            }, CstmView.class);

            _customViews = getUnmodifiable(customViews);
            _rowIdMap = Collections.unmodifiableMap(rowIdMap);
            _entityIdMap = Collections.unmodifiableMap(entityIdMap);
            _inheritableCustomViews = getUnmodifiable(inheritableCustomViews);
        }

        private MultiValuedMap<String, CstmView> ensureViewMultiMap(Map<String, CaseInsensitiveArrayListValuedMap<CstmView>> views, String schemaName)
        {
            if (!views.containsKey(schemaName))
            {
                views.put(schemaName, new CaseInsensitiveArrayListValuedMap<>());
            }
            return views.get(schemaName);
        }

        private Map<String, MultiValuedMap<String, CstmView>> getUnmodifiable(Map<String, CaseInsensitiveArrayListValuedMap<CstmView>> views)
        {
            views.values().forEach(CaseInsensitiveArrayListValuedMap::trimToSize);
            return Collections.unmodifiableMap(views);
        }

        private @NotNull
        Collection<CstmView> getCstmViews(String schemaName, String queryName, String viewName, boolean inheritableOnly)
        {
            Map<String, MultiValuedMap<String, CstmView>> customViewMap = inheritableOnly ? _inheritableCustomViews : _customViews;

            if (schemaName == null)
            {
                // all views in the container
                return _rowIdMap.values();
            }
            else if (customViewMap.containsKey(schemaName))
            {
                MultiValuedMap<String, CstmView> schemaMultiMap = customViewMap.get(schemaName);
                if (queryName != null)
                {
                    if (schemaMultiMap.containsKey(queryName))
                    {
                        Collection<CstmView> viewList = schemaMultiMap.get(queryName);

                        // view name specified
                        if (viewName != null)
                        {
                            List<CstmView> views;
                            // special case requesting the default view
                            if (StringUtils.isEmpty(viewName))
                                views = viewList.stream().filter(view -> view.getName() == null).collect(Collectors.toList());
                            else
                                views = viewList.stream().filter(view -> viewName.equals(view.getName())).collect(Collectors.toList());
                            return Collections.unmodifiableList(views);
                        }

                        return viewList;
                    }
                }
                else
                {
                    // return all views for the schema
                    Collection<CstmView> schemaViews = new ArrayList<>();
                    schemaViews.addAll(schemaMultiMap.values());

                    return schemaViews;
                }
            }
            return Collections.emptyList();
        }

        private @Nullable CstmView getForRowId(int rowId)
        {
            return _rowIdMap.get(rowId);
        }

        private @Nullable CstmView getForEntityId(String entityId)
        {
            return _entityIdMap.get(entityId);
        }

        private @NotNull Collection<CstmView> getCstmViews()
        {
            return _rowIdMap.values();
        }
    }

    public static @NotNull
    List<CstmView> getCstmViews(Container c, String schemaName, @Nullable String queryName, @Nullable String viewName,
                                @Nullable User user, boolean inheritableOnly, boolean sharedOnly)
    {
        List<CstmView> views = new ArrayList<>();

        for (CstmView view : CUSTOM_VIEW_DB_CACHE.get(c).getCstmViews(schemaName, queryName, viewName, inheritableOnly))
        {
            if (sharedOnly)
            {
                if (view.getCustomViewOwner() == null)
                    views.add(view);
            }
            else
            {
                if (user != null)
                {
                    // Custom views owned by the user first, then add shared custom views
                    if (view.getCustomViewOwner() != null && view.getCustomViewOwner().equals(user.getUserId()))
                        views.add(view);
                }
                else
                {
                    // Get all custom views regardless of owner
                    views.add(view);
                }
            }
        }

        return Collections.unmodifiableList(views);
    }

    static @Nullable CstmView getCstmView(Container c, int rowId)
    {
        return CUSTOM_VIEW_DB_CACHE.get(c).getForRowId(rowId);
    }

    static @Nullable CstmView getCstmViewByEntityId(Container c, String entityId)
    {
        return CUSTOM_VIEW_DB_CACHE.get(c).getForEntityId(entityId);
    }

    public static void uncache(Container c)
    {
        CUSTOM_VIEW_DB_CACHE.remove(c);
    }
}
