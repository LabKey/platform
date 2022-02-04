package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ParticipantCategoryCache
{
    private static final Cache<Container, ParticipantCategoryCollections> PARTICIPANT_CATEGORY_CACHE = CacheManager.getBlockingCache(
            CacheManager.UNLIMITED,
            CacheManager.DAY,
            "Participant Category Cache",
            (c, argument) -> new ParticipantCategoryCollections(c));

    private static class ParticipantCategoryCollections
    {
        private final Map<Integer, ParticipantCategoryImpl> _rowIdMap;
        private final Map<String, ParticipantCategoryImpl> _labelMap;
        private final Map<String, Collection<ParticipantCategoryImpl>> _typeMap;

        private ParticipantCategoryCollections(Container c)
        {
            Map<Integer, ParticipantCategoryImpl> rowIdMap = new HashMap<>();
            Map<String, ParticipantCategoryImpl> labelMap = new HashMap<>();
            Map<String, Collection<ParticipantCategoryImpl>> typeMap = new HashMap<>();

            new TableSelector(ParticipantGroupManager.getTableInfoParticipantCategory(), SimpleFilter.createContainerFilter(c), null).forEach(ParticipantCategoryImpl.class, pc -> {

                rowIdMap.put(pc.getRowId(), pc);
                labelMap.put(pc.getLabel(), pc);
                typeMap.computeIfAbsent(pc.getType(), (l) -> new ArrayList<>()).add(pc);
            });

            _rowIdMap = Collections.unmodifiableMap(rowIdMap);
            _labelMap = Collections.unmodifiableMap(labelMap);
            _typeMap = Collections.unmodifiableMap(typeMap);
        }

        private @Nullable ParticipantCategoryImpl getForRowId(int rowId)
        {
            return _rowIdMap.get(rowId);
        }

        private @Nullable ParticipantCategoryImpl getForLabel(String label)
        {
            return _labelMap.get(label);
        }

        private @Nullable Collection<ParticipantCategoryImpl> getForType(String type)
        {
            return _typeMap.get(type);
        }

        private @NotNull Collection<ParticipantCategoryImpl> getParticipantCategories()
        {
            return _rowIdMap.values();
        }
    }

    static @Nullable ParticipantCategoryImpl getParticipantCategory(Container c, int rowId)
    {
        return PARTICIPANT_CATEGORY_CACHE.get(c).getForRowId(rowId);
    }

    static @Nullable ParticipantCategoryImpl getParticipantCategoryForLabel(Container c, String label)
    {
        return PARTICIPANT_CATEGORY_CACHE.get(c).getForLabel(label);
    }

    static @Nullable Collection<ParticipantCategoryImpl> getParticipantCategoryForType(Container c, String type)
    {
        return PARTICIPANT_CATEGORY_CACHE.get(c).getForType(type);
    }

    static @NotNull Collection<ParticipantCategoryImpl> getParticipantCategories(Container c)
    {
        return PARTICIPANT_CATEGORY_CACHE.get(c).getParticipantCategories();
    }

    public static void uncache(Container c)
    {
        PARTICIPANT_CATEGORY_CACHE.remove(c);
    }

    public static void clearCache()
    {
        PARTICIPANT_CATEGORY_CACHE.clear();
    }
}
