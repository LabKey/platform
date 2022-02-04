package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.model.ParticipantGroup;
import org.labkey.study.StudySchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ParticipantGroupCache
{
    private static final Cache<Container, ParticipantGroupCollections> PARTICIPANT_GROUP_CACHE = CacheManager.getBlockingCache(
            CacheManager.UNLIMITED,
            CacheManager.DAY,
            "Participant Group Cache",
            (c, argument) -> new ParticipantGroupCollections(c));

    private static class ParticipantGroupCollections
    {
        private final Map<Integer, ParticipantGroup> _rowIdMap;
        private final Map<String, ParticipantGroup> _labelMap;
        private final Map<Integer, Collection<ParticipantGroup>> _categoryMap;

        private ParticipantGroupCollections(Container c)
        {
            Map<Integer, ParticipantGroup> rowIdMap = new HashMap<>();
            Map<String, ParticipantGroup> labelMap = new HashMap<>();
            Map<Integer, Collection<ParticipantGroup>> categoryMap = new HashMap<>();

            new TableSelector(StudySchema.getInstance().getTableInfoParticipantGroup(), SimpleFilter.createContainerFilter(c), null).forEach(ParticipantGroup.class, group -> {
                // get the participants assigned to this group
                Filter filter = new SimpleFilter(FieldKey.fromParts("groupId"), group.getRowId());
                Set<String> participants = new HashSet<>((new TableSelector(ParticipantGroupManager.getTableInfoParticipantGroupMap(), Collections.singleton("participantId"), filter, new Sort("participantId")).getArrayList(String.class)));
                group.setParticipantSet(participants);

                ParticipantCategoryImpl category = new TableSelector(ParticipantGroupManager.getTableInfoParticipantCategory()).getObject(group.getCategoryId(), ParticipantCategoryImpl.class);
                if (category != null)
                    group.setCategoryLabel(category.getLabel());

                rowIdMap.put(group.getRowId(), group);
                labelMap.put(group.getLabel(), group);
                categoryMap.computeIfAbsent(group.getCategoryId(), (l) -> new ArrayList<>()).add(group);
            });

            _rowIdMap = Collections.unmodifiableMap(rowIdMap);
            _labelMap = Collections.unmodifiableMap(labelMap);
            _categoryMap = Collections.unmodifiableMap(categoryMap);
        }

        private @Nullable ParticipantGroup getForRowId(int rowId)
        {
            return _rowIdMap.get(rowId);
        }

        private @Nullable ParticipantGroup getForLabel(String name)
        {
            return _labelMap.get(name);
        }

        private @Nullable Collection<ParticipantGroup> getParticipantGroupsForCategory(int categoryId)
        {
            return _categoryMap.get(categoryId);
        }

        private @NotNull Collection<ParticipantGroup> getParticipantGroups()
        {
            return _rowIdMap.values();
        }
    }

    static @Nullable ParticipantGroup getParticipantGroup(Container c, int rowId)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getForRowId(rowId);
    }

    static @Nullable ParticipantGroup getParticipantGroup(Container c, String label)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getForLabel(label);
    }

    static @NotNull Collection<ParticipantGroup> getParticipantGroupsForCategory(Container c, int categoryId)
    {
        Collection<ParticipantGroup> groups = PARTICIPANT_GROUP_CACHE.get(c).getParticipantGroupsForCategory(categoryId);
        if (groups != null)
            return groups;

        return Collections.emptyList();
    }

    static @NotNull Collection<ParticipantGroup> getParticipantGroups(Container c)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getParticipantGroups();
    }

    public static void uncache(Container c)
    {
        PARTICIPANT_GROUP_CACHE.remove(c);
    }

    public static void clearCache()
    {
        PARTICIPANT_GROUP_CACHE.clear();
    }
}
