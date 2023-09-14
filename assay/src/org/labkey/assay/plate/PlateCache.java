package org.labkey.assay.plate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.query.FieldKey;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlateCache
{
    private static final PlateLoader _loader = new PlateLoader();
    private static final Cache<String, Plate> PLATE_CACHE = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Plate Cache", _loader);
    private static final Logger LOG = LogManager.getLogger(PlateCache.class);

    private static class PlateLoader implements CacheLoader<String, Plate>
    {
        private Map<Container, List<Plate>> _containerPlateMap = new HashMap<>();            // internal collection to help un-cache all plates for a container

        @Override
        public Plate load(@NotNull String key, @Nullable Object argument)
        {
            // parse the cache key
            PlateCacheKey cacheKey = new PlateCacheKey(key);

            SimpleFilter filter = SimpleFilter.createContainerFilter(cacheKey._container);
            filter.addCondition(FieldKey.fromParts(cacheKey._type.name()), cacheKey._identifier);

            List<PlateImpl> plates = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getArrayList(PlateImpl.class);
            assert plates.size() <= 1;

            if (plates.size() == 1)
            {
                PlateImpl plate = plates.get(0);
                PlateManager.get().populatePlate(plate);
                LOG.info(String.format("Caching plate \"%s\" for folder %s", plate.getName(), cacheKey._container.getPath()));

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
                if (plate.getName() == null)
                    throw new IllegalArgumentException("Plate cannot be cached, name is null");
                if (plate.getRowId() == null)
                    throw new IllegalArgumentException("Plate cannot be cached, rowId is null");
                if (plate.getLSID() == null)
                    throw new IllegalArgumentException("Plate cannot be cached, LSID is null");

                // add the plate for the other key types
                if (cacheKey._type != PlateCacheKey.Type.rowId)
                    PLATE_CACHE.put(PlateCacheKey.getCacheKey(plate.getContainer(), plate.getRowId()), plate);
                if (cacheKey._type != PlateCacheKey.Type.lsid)
                    PLATE_CACHE.put(PlateCacheKey.getCacheKey(plate.getContainer(), Lsid.parse(plate.getLSID())), plate);
                if (cacheKey._type != PlateCacheKey.Type.name)
                    PLATE_CACHE.put(PlateCacheKey.getCacheKey(plate.getContainer(), plate.getName()), plate);

                _containerPlateMap.computeIfAbsent(cacheKey._container, k -> new ArrayList<>()).add(plate);
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
        Container c = getContainerWithIdentifier(cf, filter);

        return c != null ? PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, rowId)) : null;
    }

    public static @Nullable Plate getPlate(ContainerFilter cf, Lsid lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Lsid"), lsid);
        Container c = getContainerWithIdentifier(cf, filter);

        return c != null ? PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, lsid)) : null;
    }

    private static @Nullable Container getContainerWithIdentifier(ContainerFilter cf, SimpleFilter filter)
    {
        filter.addClause(cf.createFilterClause(AssayDbSchema.getInstance().getSchema(), FieldKey.fromParts("Container")));
        List<String> containers = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(),
                Collections.singleton("Container"),
                filter, null).getArrayList(String.class);

        if (containers.size() > 1)
            throw new IllegalStateException("More than one Plate found that matches that filter");

        if (containers.size() == 1)
        {
            return ContainerManager.getForId(containers.get(0));
        }
        return null;
    }

    public static @Nullable Plate getPlate(Container c, String name)
    {
        Plate plate = PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, name));
        return plate != null ? plate.copy() : null;
    }

    public static @Nullable Plate getPlate(Container c, Lsid lsid)
    {
        Plate plate = PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, lsid));
        return plate != null ? plate.copy() : null;
    }

    public static @NotNull Collection<Plate> getPlates(Container c)
    {
        List<Plate> plates = new ArrayList<>();
        List<Integer> ids = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(),
                Collections.singleton("RowId"),
                SimpleFilter.createContainerFilter(c), null).getArrayList(Integer.class);
        for (Integer id : ids)
        {
            plates.add(PLATE_CACHE.get(PlateCacheKey.getCacheKey(c, id)));
        }
        return plates;
    }

    public static @NotNull List<Plate> getPlateTemplates(Container c)
    {
        List<Plate> templates = new ArrayList<>();
        for (Plate plate : getPlates(c))
        {
            if (plate.isTemplate())
                templates.add(plate);
        }
        return templates.stream()
                .sorted(Comparator.comparing(Plate::getName))
                .collect(Collectors.toList());
    }

    public static void uncache(Container c)
    {
        LOG.info(String.format("Clearing cache for folder %s", c.getPath()));

        // uncache all plates for this container
        if (_loader._containerPlateMap.containsKey(c))
        {
            List<Plate> plates = new ArrayList<>(_loader._containerPlateMap.get(c));
            for (Plate plate : plates)
            {
                uncache(c, plate);
            }
            _loader._containerPlateMap.remove(c);
        }
    }

    public static void uncache(Container c, Plate plate)
    {
        LOG.info(String.format("Un-caching plate \"%s\" for folder %s", plate.getName(), c.getPath()));

        if (plate.getName() == null)
            throw new IllegalArgumentException("Plate cannot be uncached, name is null");
        if (plate.getRowId() == null)
            throw new IllegalArgumentException("Plate cannot be uncached, rowId is null");
        if (plate.getLSID() == null)
            throw new IllegalArgumentException("Plate cannot be uncached, LSID is null");

        PLATE_CACHE.remove(PlateCacheKey.getCacheKey(c, plate.getName()));
        PLATE_CACHE.remove(PlateCacheKey.getCacheKey(c, plate.getRowId()));
        PLATE_CACHE.remove(PlateCacheKey.getCacheKey(c, Lsid.parse(plate.getLSID())));

        if (_loader._containerPlateMap.containsKey(c))
            _loader._containerPlateMap.get(c).remove(plate);
    }

    public static void clearCache()
    {
        PLATE_CACHE.clear();
    }

    private static class PlateCacheKey
    {
        enum Type
        {
            rowId,
            name,
            lsid,
        }
        private Type _type;
        private Container _container;
        private Object _identifier;

        PlateCacheKey(String key)
        {
            JSONObject json = new JSONObject(key);

            _type = json.getEnum(Type.class, "type");
            _container = ContainerManager.getForId(json.getString("container"));
            _identifier = json.get("identifier");
        }

        public static String getCacheKey(Container c, String name)
        {
            return _getCacheKey(c, Type.name, name);
        }

        public static String getCacheKey(Container c, Integer plateId)
        {
            return _getCacheKey(c, Type.rowId, plateId);
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
