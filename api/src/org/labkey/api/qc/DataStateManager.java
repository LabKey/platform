/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.qc;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataStateManager
{
    private static final DataStateManager _instance = new DataStateManager();
    private static Map<String, DataStateHandler> _DataStateHandlers = new HashMap<>();
    private static final Cache<Container, DataStateCollections> DATA_STATE_DB_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "DataStates",
            (c, argument) -> new DataStateCollections(c)
    );

    private static class DataStateCollections
    {
        private final List<DataState> _dataStates;
        private final Map<Integer, DataState> _dataStateIdMap;
        private final Map<String, DataState> _dataStateLabelMap;

        private DataStateCollections(Container c)
        {
            List<DataState> dataStates = new ArrayList<>();
            Map<Integer, DataState> dataStateIdMap = new HashMap<>();
            Map<String, DataState>  dataStateLabelMap = new HashMap<>();

            new TableSelector(CoreSchema.getInstance().getTableInfoDataStates(), SimpleFilter.createContainerFilter(c), new Sort("Label")).forEach(DataState.class, dataState -> {

                dataStates.add(dataState);
                dataStateIdMap.put(dataState.getRowId(), dataState);
                dataStateLabelMap.put(dataState.getLabel(), dataState);

            });

            _dataStates = Collections.unmodifiableList(dataStates);
            _dataStateIdMap = Collections.unmodifiableMap(dataStateIdMap);
            _dataStateLabelMap = Collections.unmodifiableMap(dataStateLabelMap);
        }

        DataState getState(String label)
        {
            return _dataStateLabelMap.get(label);
        }

        DataState getState(int rowId)
        {
            return _dataStateIdMap.get(rowId);
        }

        List<DataState> getDataStates()
        {
            return _dataStates;
        }
    }

    private DataStateManager(){}

    public static DataStateManager getInstance()
    {
        return _instance;
    }

    @NotNull
    public List<DataState> getStates(Container container)
    {
        return DATA_STATE_DB_CACHE.get(container).getDataStates();
    }

    public void registerDataStateHandler(DataStateHandler handler)
    {
        String handlerType = handler.getHandlerType();
        if (!_DataStateHandlers.containsKey(handlerType))
            _DataStateHandlers.put(handlerType, handler);
        else
            throw new IllegalArgumentException("DataStateHandler '" + handlerType + "' is already registered.");
    }

    public Map<String, DataStateHandler> getRegisteredDataHandlers()
    {
        return _DataStateHandlers;
    }

    public boolean showStates(Container container)
    {
        return !getStates(container).isEmpty();
    }

    public DataState insertState(User user, DataState state)
    {
        DataState newState = Table.insert(user, CoreSchema.getInstance().getTableInfoDataStates(), state);
        DATA_STATE_DB_CACHE.remove(state.getContainer());

        return newState;
    }

    public DataState updateState(User user, DataState state)
    {
        DATA_STATE_DB_CACHE.remove(state.getContainer());
        return Table.update(user, CoreSchema.getInstance().getTableInfoDataStates(), state, state.getRowId());
    }

    public boolean deleteState(DataState state)
    {
        List<DataState> preDeleteStates = getStates(state.getContainer());
        DATA_STATE_DB_CACHE.remove(state.getContainer());
        Table.delete(CoreSchema.getInstance().getTableInfoDataStates(), state.getRowId());

        // return whether this is the last data state as it may matter for some clients
        return (preDeleteStates.size() == 1);
    }

    public DataState getStateForRowId(Container container, int rowId)
    {
        return DATA_STATE_DB_CACHE.get(container).getState(rowId);
    }

    public void clearCache(Container c)
    {
        DATA_STATE_DB_CACHE.remove(c);
    }
}
