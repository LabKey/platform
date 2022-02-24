package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.Constants;
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
            Constants.getMaxContainers(),
            CacheManager.DAY,
            "Participant Group Cache",
            (c, argument) -> new ParticipantGroupCollections(c));

    private static class ParticipantGroupCollections
    {
        private final Map<Integer, ParticipantCategoryImpl> _categoryRowIdMap;
        private final Map<String, ParticipantCategoryImpl> _categoryLabelMap;
        private final Map<String, Collection<ParticipantCategoryImpl>> _typeMap;
        private final Map<Integer, ParticipantGroup> _groupRowIdMap;
        private final Map<String, ParticipantGroup> _groupLabelMap;

        private ParticipantGroupCollections(Container c)
        {
            Map<Integer, ParticipantGroup> groupRowIdMap = new HashMap<>();
            Map<String, ParticipantGroup> groupLabelMap = new HashMap<>();
            Map<Integer, Collection<ParticipantGroup>> categoryMap = new HashMap<>();
            Map<Integer, ParticipantCategoryImpl> categoryRowIdMap = new HashMap<>();
            Map<String, ParticipantCategoryImpl> categoryLabelMap = new HashMap<>();
            Map<String, Collection<ParticipantCategoryImpl>> typeMap = new HashMap<>();

            // collect the participant groups in this container
            new TableSelector(StudySchema.getInstance().getTableInfoParticipantGroup(), SimpleFilter.createContainerFilter(c), null).forEach(ParticipantGroup.class, group -> {
                // get the participants assigned to this group
                Filter filter = new SimpleFilter(FieldKey.fromParts("groupId"), group.getRowId());
                Set<String> participants = new HashSet<>((new TableSelector(ParticipantGroupManager.getTableInfoParticipantGroupMap(), Collections.singleton("participantId"), filter, new Sort("participantId")).getArrayList(String.class)));
                group.setParticipantSet(participants);

                ParticipantCategoryImpl category = new TableSelector(ParticipantGroupManager.getTableInfoParticipantCategory()).getObject(group.getCategoryId(), ParticipantCategoryImpl.class);
                if (category != null)
                    group.setCategoryLabel(category.getLabel());

                groupRowIdMap.put(group.getRowId(), group);
                groupLabelMap.put(group.getLabel(), group);
                categoryMap.computeIfAbsent(group.getCategoryId(), (l) -> new ArrayList<>()).add(group);
            });

            // collect the participant categories in this container
            new TableSelector(ParticipantGroupManager.getTableInfoParticipantCategory(), SimpleFilter.createContainerFilter(c), null).forEach(ParticipantCategoryImpl.class, pc -> {

                // attach groups to this category
                if (categoryMap.containsKey(pc.getRowId()))
                    pc.setGroups(categoryMap.get(pc.getRowId()).toArray(ParticipantGroup[]::new));
                else
                    pc.setGroups(new ParticipantGroup[0]);
                categoryRowIdMap.put(pc.getRowId(), pc);
                categoryLabelMap.put(pc.getLabel(), pc);
                typeMap.computeIfAbsent(pc.getType(), (l) -> new ArrayList<>()).add(pc);
            });

            _categoryRowIdMap = Collections.unmodifiableMap(categoryRowIdMap);
            _categoryLabelMap = Collections.unmodifiableMap(categoryLabelMap);
            _typeMap = Collections.unmodifiableMap(typeMap);

            _groupRowIdMap = Collections.unmodifiableMap(groupRowIdMap);
            _groupLabelMap = Collections.unmodifiableMap(groupLabelMap);
        }

        private @Nullable ParticipantGroup getGroupForRowId(int rowId)
        {
            return _groupRowIdMap.get(rowId);
        }

        private @Nullable ParticipantGroup getGroupForLabel(String name)
        {
            return _groupLabelMap.get(name);
        }

        private @NotNull Collection<ParticipantGroup> getParticipantGroups()
        {
            return _groupRowIdMap.values();
        }

        private @Nullable ParticipantCategoryImpl getCategoryForRowId(int rowId)
        {
            return _categoryRowIdMap.get(rowId);
        }

        private @Nullable ParticipantCategoryImpl getCategoryForLabel(String label)
        {
            return _categoryLabelMap.get(label);
        }

        private @Nullable Collection<ParticipantCategoryImpl> getCategoryForType(String type)
        {
            return _typeMap.get(type);
        }

        private @NotNull Collection<ParticipantCategoryImpl> getParticipantCategories()
        {
            return _categoryRowIdMap.values();
        }
    }

    static @Nullable ParticipantGroup getParticipantGroup(Container c, int rowId)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getGroupForRowId(rowId);
    }

    static @Nullable ParticipantGroup getParticipantGroup(Container c, String label)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getGroupForLabel(label);
    }

    static @NotNull Collection<ParticipantGroup> getParticipantGroups(Container c)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getParticipantGroups();
    }

    static @Nullable ParticipantCategoryImpl getParticipantCategory(Container c, int rowId)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getCategoryForRowId(rowId);
    }

    static @Nullable ParticipantCategoryImpl getParticipantCategoryForLabel(Container c, String label)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getCategoryForLabel(label);
    }

    static @Nullable Collection<ParticipantCategoryImpl> getParticipantCategoryForType(Container c, String type)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getCategoryForType(type);
    }

    static @NotNull Collection<ParticipantCategoryImpl> getParticipantCategories(Container c)
    {
        return PARTICIPANT_GROUP_CACHE.get(c).getParticipantCategories();
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
