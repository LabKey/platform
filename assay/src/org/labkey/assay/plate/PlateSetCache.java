package org.labkey.assay.plate;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.assay.query.AssayDbSchema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlateSetCache
{
    private static final PlateSetCache.Loader _loader = new PlateSetCache.Loader();
    private static final Cache<String, PlateSet> PLATE_SET_CACHE = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Plate Set Cache", _loader);
    private static final Logger LOG = LogHelper.getLogger(PlateSetCache.class, "Plate set cache.");

    private static class Loader implements CacheLoader<String, PlateSet>
    {
        private final Map<Container, Set<Integer>> _containerPlateSet = new HashMap<>();            // internal collection to help un-cache all plate sets for a container

        @Override
        public PlateSet load(@NotNull String key, @Nullable Object argument)
        {
            // parse the cache key
            PlateSetCacheKey cacheKey = new PlateSetCacheKey(key);

            SimpleFilter filter = SimpleFilter.createContainerFilter(cacheKey._container);
            filter.addCondition(FieldKey.fromParts(cacheKey._type.name()), cacheKey._identifier);

            List<PlateSetImpl> plateSets = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateSet(), filter, null).getArrayList(PlateSetImpl.class);
            if (plateSets.size() == 1)
            {
                PlateSet plateSet = plateSets.get(0);
                LOG.debug(String.format("Caching plate set \"%s\" for folder %s", plateSet.getName(), cacheKey._container.getPath()));

                // add all cache keys for this plate set
                addCacheKeys(cacheKey, plateSet);
                return plateSet;
            }
            return null;
        }

        private void addCacheKeys(PlateSetCacheKey cacheKey, PlateSet plateSet)
        {
            if (plateSet != null)
            {
                if (plateSet.getPlateSetId() == null)
                    throw new IllegalArgumentException("Plate set cannot be cached, plateSetId is null");
                if (plateSet.getRowId() == null)
                    throw new IllegalArgumentException("Plate set cannot be cached, rowId is null");

                // add the plate for the other key types
                if (cacheKey._type != PlateSetCacheKey.Type.rowId)
                    PLATE_SET_CACHE.put(PlateSetCacheKey.getCacheKey(plateSet.getContainer(), plateSet.getRowId()), plateSet);
                if (cacheKey._type != PlateSetCacheKey.Type.plateSetId)
                    PLATE_SET_CACHE.put(PlateSetCacheKey.getCacheKey(plateSet.getContainer(), plateSet.getPlateSetId()), plateSet);

                _containerPlateSet.computeIfAbsent(cacheKey._container, k -> new HashSet<>()).add(plateSet.getRowId());
            }
        }
    }

    public static @Nullable PlateSet getPlateSet(ContainerFilter cf, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        Container c = PlateManager.getContainerWithPlateSetIdentifier(cf, filter);

        return c != null ? PLATE_SET_CACHE.get(PlateSetCacheKey.getCacheKey(c, rowId)) : null;
    }

    public static @Nullable PlateSet getPlateSet(Container c, int rowId)
    {
        return PLATE_SET_CACHE.get(PlateSetCacheKey.getCacheKey(c, rowId));
    }

    public static @Nullable PlateSet getPlateSet(Container c, String plateSetId)
    {
        return PLATE_SET_CACHE.get(PlateSetCacheKey.getCacheKey(c, plateSetId));
    }

    public static void uncache(Container c)
    {
        LOG.debug(String.format("Clearing plate set cache for folder %s", c.getPath()));

        // uncache all plate sets for this container
        if (_loader._containerPlateSet.containsKey(c))
        {
            Set<Integer> rowIds = new HashSet<>(_loader._containerPlateSet.get(c));
            for (Integer rowId : rowIds)
            {
                uncache(c, rowId);
            }
        }
    }

    public static void uncache(Container c, int rowId)
    {
        // noop if the plate doesn't exist in the cache
        String key = PlateSetCacheKey.getCacheKey(c, rowId);
        if (PLATE_SET_CACHE.getKeys().contains(key))
        {
            PlateSet plateSet = getPlateSet(c, rowId);
            if (plateSet != null)
                uncache(c, getPlateSet(c, rowId));
            else
                throw new IllegalStateException(String.format("Expected plate set with rowId : \"%d\" to be in the cache.", rowId));
        }
    }

    public static void uncache(Container c, PlateSet plateSet)
    {
        LOG.debug(String.format("Un-caching plate set \"%s\"", plateSet.getPlateSetId()));
        if (plateSet.getPlateSetId() == null)
            throw new IllegalArgumentException("Plate set cannot be uncached, plateSetId is null");
        if (plateSet.getRowId() == null)
            throw new IllegalArgumentException("Plate set cannot be uncached, rowId is null");

        PLATE_SET_CACHE.remove(PlateSetCacheKey.getCacheKey(c, plateSet.getPlateSetId()));
        PLATE_SET_CACHE.remove(PlateSetCacheKey.getCacheKey(c, plateSet.getRowId()));

        if (_loader._containerPlateSet.containsKey(plateSet.getContainer()))
            _loader._containerPlateSet.get(plateSet.getContainer()).remove(plateSet.getRowId());
    }

    public static void clearCache()
    {
        PLATE_SET_CACHE.clear();
        _loader._containerPlateSet.clear();
    }

    private static class PlateSetCacheKey
    {
        enum Type
        {
            rowId,
            plateSetId,
        }

        private final Type _type;
        private final Container _container;
        private final Object _identifier;

        PlateSetCacheKey(String key)
        {
            JSONObject json = new JSONObject(key);

            _type = json.getEnum(PlateSetCache.PlateSetCacheKey.Type.class, "type");
            _container = ContainerManager.getForId(json.getString("container"));
            _identifier = json.get("identifier");
        }

        public static String getCacheKey(Container c, String plateSetId)
        {
            return _getCacheKey(c, Type.plateSetId, plateSetId);
        }

        public static String getCacheKey(Container c, Integer rowId)
        {
            return _getCacheKey(c, Type.rowId, rowId);
        }

        private static String _getCacheKey(Container c, Type type, Object identifier)
        {
            JSONObject json = new JSONObject();
            json.put("container", c.getId());
            json.put("type", type.name());
            json.put("identifier", identifier);

            return json.toString();
        }
    }
}
