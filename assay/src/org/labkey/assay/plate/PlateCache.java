package org.labkey.assay.plate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.query.FieldKey;
import org.labkey.assay.plate.model.PlateBean;
import org.labkey.assay.plate.query.PlateTable;
import org.labkey.assay.query.AssayDbSchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlateCache
{
    private static final PlateLoader _loader = new PlateLoader();
    private static final Cache<String, Plate> PLATE_CACHE = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Plate Cache", _loader);
    private static final Logger LOG = LogManager.getLogger(PlateCache.class);

    private static class PlateLoader implements CacheLoader<String, Plate>
    {
        private final Map<Container, Set<Integer>> _containerPlateMap = new HashMap<>();            // internal collection to help un-cache all plates for a container

        @Override
        public Plate load(@NotNull String key, @Nullable Object argument)
        {
            // parse the cache key
            PlateCacheKey cacheKey = new PlateCacheKey(key);

            SimpleFilter filter = SimpleFilter.createContainerFilter(cacheKey._container);
            filter.addCondition(FieldKey.fromParts(cacheKey._type.name()), cacheKey._identifier);

            List<PlateBean> plates = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getArrayList(PlateBean.class);
            assert plates.size() <= 1;

            if (plates.size() == 1)
            {
                PlateBean bean = plates.get(0);

                Plate plate = PlateManager.get().populatePlate(bean);
                LOG.debug(String.format("Caching plate \"%s\" for folder %s", plate.getName(), cacheKey._container.getPath()));

                // add all cache keys for this plate
                addCacheKeys(cacheKey, plate);
                return plate;
            }
            return null;
        }

        private void addCacheKeys(PlateCacheKey cacheKey, Plate plate)
        {
            if (plate != null)
            {
                if (plate.getPlateId() == null)
                    throw new IllegalArgumentException("Plate cannot be cached, plateId is null");
                if (plate.getRowId() == null)
                    throw new IllegalArgumentException("Plate cannot be cached, rowId is null");
                if (plate.getLSID() == null)
                    throw new IllegalArgumentException("Plate cannot be cached, LSID is null");

                // add the plate for the other key types
                if (cacheKey._type != PlateCacheKey.Type.rowId)
                    PLATE_CACHE.put(PlateCacheKey.getCacheKey(plate.getContainer(), plate.getRowId()), plate);
                if (cacheKey._type != PlateCacheKey.Type.lsid)
                    PLATE_CACHE.put(PlateCacheKey.getCacheKey(plate.getContainer(), Lsid.parse(plate.getLSID())), plate);
                if (cacheKey._type != PlateCacheKey.Type.plateId)
                    PLATE_CACHE.put(PlateCacheKey.getCacheKey(plate.getContainer(), plate.getPlateId()), plate);

                _containerPlateMap.computeIfAbsent(cacheKey._container, k -> new HashSet<>()).add(plate.getRowId());
            }
        }
    }

    public static @Nullable Plate getPlate(Container c, int rowId)
    {
        Plate plate = PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, rowId));
        // We allow plates to be mutated, return a copy of the cached object which still references the
        // original wells and well groups
        return plate != null ? plate.copy() : null;
    }

    public static @Nullable Plate getPlate(ContainerFilter cf, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        Container c = PlateManager.getContainerWithPlateIdentifier(cf, filter);

        return c != null ? PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, rowId)) : null;
    }

    public static @Nullable Plate getPlate(ContainerFilter cf, Lsid lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Lsid"), lsid);
        Container c = PlateManager.getContainerWithPlateIdentifier(cf, filter);

        return c != null ? PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, lsid)) : null;
    }

    public static @Nullable Plate getPlate(ContainerFilter cf, String plateId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("plateId"), plateId);
        Container c = PlateManager.getContainerWithPlateIdentifier(cf, filter);

        return c != null ? PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, plateId)) : null;
    }

    public static @Nullable Plate getPlate(Container c, String plateId)
    {
        Plate plate = PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, plateId));
        return plate != null ? plate.copy() : null;
    }

    public static @Nullable Plate getPlate(Container c, Lsid lsid)
    {
        Plate plate = PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, lsid));
        return plate != null ? plate.copy() : null;
    }

    private static @NotNull List<Integer> getPlateIDs(Container c, @Nullable SimpleFilter filter)
    {
        SimpleFilter plateFilter = SimpleFilter.createContainerFilter(c);
        if (filter != null)
        {
            for (SimpleFilter.FilterClause clause : filter.getClauses())
                plateFilter.addClause(clause);
        }

        return new TableSelector(
                AssayDbSchema.getInstance().getTableInfoPlate(),
                Collections.singleton(PlateTable.Column.RowId.name()),
                plateFilter,
                new Sort(PlateTable.Column.RowId.name())
        ).getArrayList(Integer.class);
    }

    private static @NotNull List<Plate> getPlates(Container c, @Nullable SimpleFilter filter)
    {
        List<Integer> ids = getPlateIDs(c, filter);
        return ids.stream().map(id -> PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, id))).toList();
    }

    public static @NotNull List<Plate> getPlates(Container c)
    {
        return getPlates(c, null);
    }

    public static @NotNull List<Plate> getPlatesForPlateSet(Container c, Integer plateSetRowId)
    {
        return getPlates(c, new SimpleFilter(FieldKey.fromParts(PlateTable.Column.PlateSet.name()), plateSetRowId));
    }

    public static @NotNull List<Plate> getPlateTemplates(Container c)
    {
        return getPlates(c, new SimpleFilter(FieldKey.fromParts(PlateTable.Column.Template.name()), true));
    }

    public static void uncache(Container c)
    {
        LOG.debug(String.format("Clearing cache for folder %s", c.getPath()));

        // uncache all plates for this container
        if (_loader._containerPlateMap.containsKey(c))
        {
            Set<Integer> rowIds = new HashSet<>(_loader._containerPlateMap.get(c));
            for (Integer rowId : rowIds)
            {
                uncache(c, rowId);
            }
        }
    }

    public static void uncache(Container c, int rowId)
    {
        // noop if the plate doesn't exist in the cache
        String key = PlateCacheKey.getCacheKey(c, rowId);
        if (PLATE_CACHE.getKeys().contains(key))
        {
            Plate plate = getPlate(c, rowId);
            if (plate != null)
                uncache(c, plate);
            else
                throw new IllegalStateException(String.format("Expected plate with rowId : \"%d\" to be in the cache.", rowId));
        }
    }

    public static void uncache(Container c, Plate plate)
    {
        LOG.debug(String.format("Un-caching plate \"%s\" for folder %s", plate.getPlateId(), c.getPath()));

        if (plate.getPlateId() == null)
            throw new IllegalArgumentException("Plate cannot be uncached, plateId is null");
        if (plate.getRowId() == null)
            throw new IllegalArgumentException("Plate cannot be uncached, rowId is null");
        if (plate.getLSID() == null)
            throw new IllegalArgumentException("Plate cannot be uncached, LSID is null");

        PLATE_CACHE.remove(PlateCacheKey.getCacheKey(c, plate.getPlateId()));
        PLATE_CACHE.remove(PlateCacheKey.getCacheKey(c, plate.getRowId()));
        PLATE_CACHE.remove(PlateCacheKey.getCacheKey(c, Lsid.parse(plate.getLSID())));

        if (_loader._containerPlateMap.containsKey(c))
            _loader._containerPlateMap.get(c).remove(plate.getRowId());
    }

    public static void uncache(Container c, PlateSet plateSet)
    {
        getPlateIDs(c, new SimpleFilter(FieldKey.fromParts(PlateTable.Column.PlateSet.name()), plateSet.getRowId()))
                .forEach(plateId -> uncache(c, plateId));
    }

    public static void clearCache()
    {
        PLATE_CACHE.clear();
        _loader._containerPlateMap.clear();
    }

    private static class PlateCacheKey
    {
        enum Type
        {
            rowId,
            plateId,
            lsid,
        }

        private final Type _type;
        private final Container _container;
        private final Object _identifier;

        PlateCacheKey(String key)
        {
            JSONObject json = new JSONObject(key);

            _type = json.getEnum(Type.class, "type");
            _container = ContainerManager.getForId(json.getString("container"));
            _identifier = json.get("identifier");
        }

        public static String getCacheKey(Container c, String plateId)
        {
            return _getCacheKey(c, Type.plateId, plateId);
        }

        public static String getCacheKey(Container c, Integer rowId)
        {
            return _getCacheKey(c, Type.rowId, rowId);
        }

        public static String getCacheKey(Container c, Lsid lsid)
        {
            return _getCacheKey(c, Type.lsid, lsid.toString());
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
