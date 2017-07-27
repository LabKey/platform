package org.labkey.pipeline.trigger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.trigger.PipelineTriggerConfig;
import org.labkey.api.pipeline.trigger.PipelineTriggerRegistry;
import org.labkey.api.pipeline.trigger.PipelineTriggerType;
import org.labkey.api.query.FieldKey;
import org.labkey.pipeline.api.PipelineSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

public class PipelineTriggerRegistryImpl implements PipelineTriggerRegistry
{
    private final Map<String, PipelineTriggerType> REGISTERED_TYPES = new ConcurrentSkipListMap<>();

    @Override
    public void register(PipelineTriggerType type)
    {
        if (REGISTERED_TYPES.containsKey(type.getName()))
            throw new IllegalStateException("A pipeline trigger type has already been registered for this name: " + type.getName());
        REGISTERED_TYPES.put(type.getName(), type);

        // see if there are any existing configs for this PipelineTriggerType that need to be started
        type.startAll();
    }

    @Override
    public Collection<PipelineTriggerType> getTypes()
    {
        return Collections.unmodifiableCollection(REGISTERED_TYPES.values());
    }

    @Override
    public @Nullable PipelineTriggerType getTypeByName(String name)
    {
        return name != null ? REGISTERED_TYPES.get(name) : null;
    }

    @Override
    public <C extends PipelineTriggerConfig> Collection<C> getConfigs(Container c, PipelineTriggerType<C> type, String name, boolean enabledOnly)
    {
        Sort sort = new Sort(FieldKey.fromParts("RowId"));
        SimpleFilter filter = null != c ? SimpleFilter.createContainerFilter(c) : new SimpleFilter();
        if (type != null)
            filter.addCondition(FieldKey.fromParts("Type"), type.getName());
        if (name != null)
            filter.addCondition(FieldKey.fromParts("Name"), name);
        if (enabledOnly)
            filter.addCondition(FieldKey.fromParts("Enabled"), true);

        TableSelector selector = new TableSelector(PipelineSchema.getInstance().getTableInfoTriggerConfigurations(), filter, sort);
        ArrayList<C> configs = new ArrayList<>();

        // filter for all of the PipelineTriggerConfigs for the given container/type/name (all nullable) and then
        // return the collection of configs for their given subclass
        selector.forEach(rs -> {
            String typeName = rs.getString("Type");
            PipelineTriggerType triggerType = PipelineTriggerRegistry.get().getTypeByName(typeName);
            if (triggerType != null)
                configs.add((C) triggerType.createConfig(rs));
        });

        return Collections.unmodifiableCollection(configs);
    }

    @Override
    public <C extends PipelineTriggerConfig> C getConfigByName(@NotNull Container c, String name)
    {
        if (name == null)
            return null;

        Collection<C> configs = getConfigs(c, null, name, false);
        return configs.size() == 1 ? configs.iterator().next() : null;
    }
}
