package org.labkey.api.reports.model;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.cache.Wrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: 9/29/2014
 * Time: 11:09 AM
 */
public class ViewCategoryCache
{
    private static final ViewCategoryCache INSTANCE = new ViewCategoryCache();

    private final StringKeyCache<ViewCategoryCollections> VIEW_CATEGORY_CACHE = new BlockingStringKeyCache<>(new DatabaseCache<Wrapper<ViewCategoryCollections>>(CoreSchema.getInstance().getSchema().getScope(), 300, "View Category"), new CacheLoader<String, ViewCategoryCollections>(){
        @Override
        public ViewCategoryCollections load(String key, @Nullable Object argument)
        {
            return new ViewCategoryCollections(key);
        }
    });

    private ViewCategoryCache()
    {
    }

    static ViewCategoryCache get()
    {
        return INSTANCE;
    }

    ViewCategory getViewCategory(String cid, int id)
    {
        return getCollections(cid).getViewCategory(id);
    }

    ViewCategory getViewCategory(String cid, String... parts)
    {
        if (parts.length > 2)
            throw new IllegalArgumentException("Only two view category levels are supported at this time");

        return getCollections(cid).getViewCategory(parts);
    }

    List<ViewCategory> getSubcategories(String cid, int parentId)
    {
        // "Uncategorized" fakeo category doesn't have a Container
        if (null == cid)
            return Collections.emptyList();

        return getCollections(cid).getSubcategories(parentId);
    }

    List<ViewCategory> getAllCategories(String cid)
    {
        return getCollections(cid).getViewCategories();
    }

    void clear(Container c)
    {
        VIEW_CATEGORY_CACHE.remove(c.getId());
    }

    private ViewCategoryCollections getCollections(String cid)
    {
        assert null != cid;

        return VIEW_CATEGORY_CACHE.get(cid);
    }

    private static class ViewCategoryCollections
    {
        private final Map<Integer, ViewCategory> _rowIdMap;
        private final MultiMap<Integer, ViewCategory> _childrenMap;
        private final Map<Path, ViewCategory> _pathMap;

        private ViewCategoryCollections(String cid)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), cid);
            ViewCategoryManager mgr = ViewCategoryManager.getInstance();

            Collection<ViewCategory> categories = new TableSelector(mgr.getTableInfoCategories(), filter, null).getCollection(ViewCategory.class);

            Map<Integer, ViewCategory> rowIdMap = new HashMap<>();

            for (ViewCategory category : categories)
            {
                assert null == rowIdMap.put(category.getRowId(), category);
            }

            _rowIdMap = Collections.unmodifiableMap(rowIdMap);

            Map<Path, ViewCategory> pathMap = new HashMap<>();
            _childrenMap = new MultiHashMap<>();

            for (ViewCategory category : categories)
            {
                Integer parentId = category.getParent();
                final Path path;

                if (null != parentId)
                {
                    _childrenMap.put(parentId, category);
                    ViewCategory parent = _rowIdMap.get(parentId);
                    // Note: Only supports two levels of hierarchy
                    path = new Path(parent.getLabel(), category.getLabel());
                }
                else
                {
                    path = new Path(category.getLabel());
                }

                assert null == pathMap.put(path, category);
            }

            _pathMap = Collections.unmodifiableMap(pathMap);
        }

        private ViewCategory getViewCategory(int id)
        {
            return _rowIdMap.get(id);
        }

        private List<ViewCategory> getViewCategories()
        {
            return new ArrayList<>(_rowIdMap.values());
        }

        private List<ViewCategory> getSubcategories(int id)
        {
            Collection<ViewCategory> children = _childrenMap.get(id);

            if (null == children)
                return Collections.emptyList();
            else
                return Collections.unmodifiableList(new ArrayList<>(children));
        }

        public ViewCategory getViewCategory(String[] parts)
        {
            return _pathMap.get(new Path(parts));
        }
    }
}
