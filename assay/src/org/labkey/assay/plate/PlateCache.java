package org.labkey.assay.plate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlateCache
{
    private static final Cache<Container, PlateCollections> PLATE_COLLECTIONS_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "Plate Cache", (c, argument) -> new PlateCollections(c));
    private static final Logger LOG = LogManager.getLogger(PlateCache.class);

    private static class PlateCollections
    {
        private final Map<Integer, Plate> _rowIdMap;
        private final Map<String, Plate> _nameMap;
        private final Map<Lsid, Plate> _lsidMap;
        private final List<Plate> _templates;

        private PlateCollections(Container c)
        {
            Map<Integer, Plate> rowIdMap = new HashMap<>();
            Map<String, Plate> nameMap = new HashMap<>();
            Map<Lsid, Plate> lsidMap = new HashMap<>();
            List<Plate> templates = new ArrayList<>();

            new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), SimpleFilter.createContainerFilter(c), null).forEach(PlateImpl.class, plate -> {
                PlateManager.get().populatePlate(plate);

                rowIdMap.put(plate.getRowId(), plate);
                if (nameMap.containsKey(plate.getName()))
                {
                    LOG.error(String.format("A duplicate Plate name : %s was found in the same folder : %s. We recommend that the duplicate plate(s) are deleted.", plate.getName(), c.getPath()));
                }
                nameMap.put(plate.getName(), plate);
                lsidMap.put(Lsid.parse(plate.getLSID()), plate);
                if (plate.isTemplate())
                    templates.add(plate);
            });

            templates.sort(Comparator.comparing(Plate::getName));

            _templates = templates;
            _rowIdMap = rowIdMap;
            _nameMap = nameMap;
            _lsidMap = lsidMap;
        }

        private @Nullable Plate getForRowId(int rowId)
        {
            return _rowIdMap.get(rowId);
        }

        private @Nullable Plate getForName(String name)
        {
            return _nameMap.get(name);
        }

        private @Nullable Plate getForLsid(Lsid lsid)
        {
            return _lsidMap.get(lsid);
        }

        private @NotNull List<Plate> getPlateTemplates()
        {
            return _templates;
        }
    }

    static @Nullable Plate getPlate(Container c, int rowId)
    {
        return PLATE_COLLECTIONS_CACHE.get(c).getForRowId(rowId);
    }

    static @Nullable Plate getPlate(Container c, String name)
    {
        return PLATE_COLLECTIONS_CACHE.get(c).getForName(name);
    }

    static @Nullable Plate getPlate(Container c, Lsid lsid)
    {
        return PLATE_COLLECTIONS_CACHE.get(c).getForLsid(lsid);
    }

    static @NotNull List<Plate> getPlateTemplates(Container c)
    {
        return PLATE_COLLECTIONS_CACHE.get(c).getPlateTemplates();
    }

    public static void uncache(Container c)
    {
        PLATE_COLLECTIONS_CACHE.remove(c);
    }

    public static void clearCache()
    {
        PLATE_COLLECTIONS_CACHE.clear();
    }
}
