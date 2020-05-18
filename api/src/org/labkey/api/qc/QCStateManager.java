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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
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

public class QCStateManager
{
    private static final QCStateManager _instance = new QCStateManager();
    private Map<String, QCStateHandler> _QCStateHandlers = new HashMap<>();
    private static final Cache<Container, QCStateCollections> QC_STATE_DB_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "QCStates", new CacheLoader<Container, QCStateCollections>()
    {
        @Override
        public QCStateCollections load(Container c, @Nullable Object argument)
        {
            return new QCStateCollections(c);
        }
    });

    private static class QCStateCollections
    {
        private final List<QCState> _qcStates;
        private final Map<Integer, QCState> _qcStateIdMap;
        private final Map<String, QCState> _qcStateLabelMap;

        private QCStateCollections(Container c)
        {
            List<QCState> qcStates = new ArrayList<>();
            Map<Integer, QCState> qcStateIdMap = new HashMap<>();
            Map<String, QCState>  qcStateLabelMap = new HashMap<>();

            new TableSelector(CoreSchema.getInstance().getTableInfoQCState(), SimpleFilter.createContainerFilter(c), new Sort("Label")).forEach(qcState -> {

                qcStates.add(qcState);
                qcStateIdMap.put(qcState.getRowId(), qcState);
                qcStateLabelMap.put(qcState.getLabel(), qcState);

            }, QCState.class);

            _qcStates = Collections.unmodifiableList(qcStates);
            _qcStateIdMap = Collections.unmodifiableMap(qcStateIdMap);
            _qcStateLabelMap = Collections.unmodifiableMap(qcStateLabelMap);
        }

        QCState getQCState(String label)
        {
            return _qcStateLabelMap.get(label);
        }

        QCState getQCState(int rowId)
        {
            return _qcStateIdMap.get(rowId);
        }

        List<QCState> getQcStates()
        {
            return _qcStates;
        }
    }

    private QCStateManager(){}

    public static QCStateManager getInstance()
    {
        return _instance;
    }

    @NotNull
    public List<QCState> getQCStates(Container container)
    {
        return QC_STATE_DB_CACHE.get(container).getQcStates();
    }

    public void registerQCHandler(QCStateHandler handler) //TODO RP: should be public, or protected?
    {
        String handlerType = handler.getHandlerType();
        if (!_QCStateHandlers.containsKey(handlerType))
            _QCStateHandlers.put(handlerType, handler);
        else
            throw new IllegalArgumentException("QCStateHandler '" + handlerType + "' is already registered.");
    }

    public Map<String, QCStateHandler> getRegisteredQCHandlers()
    {
        return _QCStateHandlers;
    }

    public void getHandler(Container container)
    {
        int i = 1;
    }

    public boolean showQCStates(Container container)
    {
        return !getQCStates(container).isEmpty();
    }

    public QCState insertQCState(User user, QCState state)
    {
        QCState newState = Table.insert(user, CoreSchema.getInstance().getTableInfoQCState(), state);
        QC_STATE_DB_CACHE.remove(state.getContainer());

        return newState;
    }

    public QCState updateQCState(User user, QCState state)
    {
        QC_STATE_DB_CACHE.remove(state.getContainer());
        return Table.update(user, CoreSchema.getInstance().getTableInfoQCState(), state, state.getRowId());
    }

    public boolean deleteQCState(QCState state)
    {
        List<QCState> preDeleteStates = getQCStates(state.getContainer());
        QC_STATE_DB_CACHE.remove(state.getContainer());
        Table.delete(CoreSchema.getInstance().getTableInfoQCState(), state.getRowId());

        // return whether this is the last QC states as it may matter for some clients
        return (preDeleteStates.size() == 1);
    }

    public QCState getQCStateForRowId(Container container, int rowId)
    {
        return QC_STATE_DB_CACHE.get(container).getQCState(rowId);
    }

    public void clearCache(Container c)
    {
        QC_STATE_DB_CACHE.remove(c);
    }
}
