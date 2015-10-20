package org.labkey.query.persist;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by klum on 10/16/2015.
 */
public class CustomViewCache
{
    private static final Cache<Container, CustomViewCollections> CUSTOM_VIEW_DB_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "Database Custom View Cache", new CacheLoader<Container, CustomViewCollections>()
    {
        @Override
        public CustomViewCollections load(Container c, @Nullable Object argument)
        {
            return new CustomViewCollections(c);
        }
    });

    private static class CustomViewCollections
    {
        // schemaName, queryName, viewName keys
        Map<String, Map<String, List<CstmView>>> _customViews;
        Map<String, Map<String, List<CstmView>>> _inheritableCustomViews;
        Map<Integer, CstmView> _rowIdMap;
        Map<String, CstmView> _entityIdMap;

        private CustomViewCollections(Container c)
        {
            Map<String, Map<String, List<CstmView>>> customViews = new HashMap<>();
            Map<String, Map<String, List<CstmView>>> inheritableCustomViews = new HashMap<>();
            Map<Integer, CstmView> rowIdMap = new HashMap<>();
            Map<String, CstmView> entityIdMap = new HashMap<>();

            new TableSelector(QueryManager.get().getTableInfoCustomView(), SimpleFilter.createContainerFilter(c), null).forEach(cstmView -> {

                List<CstmView> viewMap = ensureViewList(customViews, cstmView.getSchema(), cstmView.getQueryName());
                List<CstmView> inheritableViewMap = ensureViewList(inheritableCustomViews, cstmView.getSchema(), cstmView.getQueryName());

                viewMap.add(cstmView);
                rowIdMap.put(cstmView.getCustomViewId(), cstmView);
                entityIdMap.put(cstmView.getEntityId(), cstmView);
                if ((cstmView.getFlags() & QueryManager.FLAG_INHERITABLE) != 0)
                {
                    inheritableViewMap.add(cstmView);
                }

            }, CstmView.class);

            _customViews = Collections.unmodifiableMap(customViews);
            _rowIdMap = Collections.unmodifiableMap(rowIdMap);
            _entityIdMap = Collections.unmodifiableMap(entityIdMap);
            _inheritableCustomViews = Collections.unmodifiableMap(inheritableCustomViews);
        }

        private List<CstmView> ensureViewList(Map<String, Map<String, List<CstmView>>> views,
                                                    String schemaName, String queryName)
        {
            if (!views.containsKey(schemaName))
            {
                views.put(schemaName, new LinkedHashMap<>());
            }
            Map<String, List<CstmView>> queryMap = views.get(schemaName);

            if (!queryMap.containsKey(queryName))
            {
                queryMap.put(queryName, new ArrayList<>());
            }
            return queryMap.get(queryName);
        }

        private @NotNull
        Collection<CstmView> getCstmViews(String schemaName, String queryName, String viewName, boolean inheritableOnly)
        {
            Map<String, Map<String, List<CstmView>>> customViewMap = inheritableOnly ? _inheritableCustomViews : _customViews;

            if (schemaName == null)
            {
                // all views in the container
                return _rowIdMap.values();
            }
            else if (customViewMap.containsKey(schemaName))
            {
                Map<String, List<CstmView>> queryMap = customViewMap.get(schemaName);
                if (queryName != null)
                {
                    if (queryMap.containsKey(queryName))
                    {
                        List<CstmView> viewList = queryMap.get(queryName);

                        // view name specified
                        if (viewName != null)
                        {
                            List<CstmView> views = viewList.stream().filter(view -> viewName.equals(view.getName())).collect(Collectors.toList());
                            return Collections.unmodifiableList(views);
                        }
                        else
                            return viewList;
                    }
                }
                else
                {
                    // return all views for the schema
                    List<CstmView> schemaViews = new ArrayList<>();
                    queryMap.values().forEach(schemaViews::addAll);

                    return schemaViews;
                }
            }
            return Collections.EMPTY_LIST;
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
        //assert schemaName != null : "schemaName must be specified";

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

    static @NotNull Collection<CstmView> getCstmViews(Container c)
    {
        return CUSTOM_VIEW_DB_CACHE.get(c).getCstmViews();
    }

    public static void uncache(Container c)
    {
        CUSTOM_VIEW_DB_CACHE.remove(c);
    }
}
