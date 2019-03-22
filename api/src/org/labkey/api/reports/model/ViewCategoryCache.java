/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.reports.model;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.StringKeyCache;
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

    private final StringKeyCache<ViewCategoryCollections> VIEW_CATEGORY_CACHE = new BlockingStringKeyCache<>(new DatabaseCache<>(CoreSchema.getInstance().getSchema().getScope(), 300, "View Category"), (key, argument) -> new ViewCategoryCollections(key));

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

    List<ViewCategory> getTopLevelCategories(String cid)
    {
        return getCollections(cid).getSubcategories(null);
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
        private final MultiValuedMap<Integer, ViewCategory> _childrenMap;
        private final Map<Path, ViewCategory> _pathMap;

        private ViewCategoryCollections(String cid)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), cid);
            ViewCategoryManager mgr = ViewCategoryManager.getInstance();

            Collection<ViewCategory> categories = new TableSelector(mgr.getTableInfoCategories(), filter, null).getCollection(ViewCategory.class);

            Map<Integer, ViewCategory> rowIdMap = new HashMap<>();

            for (ViewCategory category : categories)
            {
                ViewCategory previous = rowIdMap.put(category.getRowId(), category);
                assert null == previous;
            }

            _rowIdMap = Collections.unmodifiableMap(rowIdMap);

            Map<Path, ViewCategory> pathMap = new HashMap<>();
            _childrenMap = new ArrayListValuedHashMap<>();

            for (ViewCategory category : categories)
            {
                @Nullable Integer parentId = category.getParent();
                // Note: ParentId may be null, but ArrayListValuedHashMap supports null keys, so we use this to collect the top-level categories
                _childrenMap.put(parentId, category);

                final Path path;

                if (null != parentId)
                {
                    ViewCategory parent = _rowIdMap.get(parentId);
                    // Note: Only supports two levels of hierarchy
                    path = new Path(parent.getLabel(), category.getLabel());
                }
                else
                {
                    path = new Path(category.getLabel());
                }

                ViewCategory previous = pathMap.put(path, category);
                assert null == previous;
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

        private List<ViewCategory> getSubcategories(@Nullable Integer id)
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
