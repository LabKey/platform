/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.collections.StringHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataColorManager
{
    /** Maximum number of data colors (active + archived) allowed per container. */
    public static final int MAX_DATA_COLORS = 200;

    private static final DataColorManager _instance = new DataColorManager();
    private static final Cache<Container, DataColorCollections> CACHE = CacheManager.getBlockingCache(
            CacheManager.UNLIMITED, CacheManager.DAY, "Data colors", (c, argument) -> new DataColorCollections(c));
    private static final Map<String, DataColorHandler> _handlers = new HashMap<>();

    private static class DataColorCollections
    {
        private final List<DataColor> _colors;
        private final Map<Long, DataColor> _byRowId;
        private final Map<String, DataColor> _byLabel;

        private DataColorCollections(Container c)
        {
            List<DataColor> colors = new ArrayList<>();
            Map<Long, DataColor> byRowId = new LongHashMap<>();
            Map<String, DataColor> byLabel = new StringHashMap<>();

            new TableSelector(ExperimentServiceImpl.get().getTinfoDataColors(), SimpleFilter.createContainerFilter(c), new Sort("Label"))
                    .forEach(DataColor.class, color -> {
                        colors.add(color);
                        byRowId.put((long) color.getRowId(), color);
                        byLabel.put(color.getLabel(), color);
                    });

            _colors = Collections.unmodifiableList(colors);
            _byRowId = Collections.unmodifiableMap(byRowId);
            _byLabel = Collections.unmodifiableMap(byLabel);
        }
    }

    private DataColorManager() {}

    public static DataColorManager getInstance()
    {
        return _instance;
    }

    public interface DataColorHandler
    {
        String getHandlerType();

        boolean isColorInUse(Container container, long colorRowId);
    }

    public void registerHandler(DataColorHandler handler)
    {
        String type = handler.getHandlerType();
        if (_handlers.containsKey(type))
            throw new IllegalArgumentException("DataColorHandler '" + type + "' is already registered.");
        _handlers.put(type, handler);
    }

    public boolean isInUse(Container container, long colorRowId)
    {
        for (DataColorHandler handler : _handlers.values())
        {
            if (handler.isColorInUse(container, colorRowId))
                return true;
        }
        return false;
    }

    @NotNull
    public List<DataColor> getColors(Container container)
    {
        return CACHE.get(container)._colors;
    }

    @NotNull
    public List<DataColor> getActiveColors(Container container)
    {
        return getColors(container).stream().filter(c -> !c.isArchived()).toList();
    }

    @Nullable
    public DataColor getColorForRowId(Container container, Long rowId)
    {
        return rowId == null ? null : CACHE.get(container)._byRowId.get(rowId);
    }

    @Nullable
    public DataColor getColorForLabel(Container container, String label)
    {
        return label == null ? null : CACHE.get(container)._byLabel.get(label);
    }

    public void clearCache(Container c)
    {
        CACHE.remove(c);
    }
}
