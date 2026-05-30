/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.api.assay.plate;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.qc.AbstractManageDataStatesForm;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateHandler;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PlateDataStateManager implements DataStateHandler
{
    public static final String HANDLER_NAME = "PlateDataStateHandler";
    private static final PlateDataStateManager _instance = new PlateDataStateManager();

    /**
     * Maps to DataStates for plate related operations. Initially, there is only a single data state
     * supported, but plans are to add more over time.
     */
    public enum StateType
    {
        Excluded("Excludes data rows from certain operations.",
                Collections.emptySet());

        final Set<DataOperation> _permittedOps;
        final String _description;

        StateType(String description, Set<DataOperation> permittedOps)
        {
            _description = description;
            _permittedOps = permittedOps;
        }

        public Set<DataOperation> getPermittedOps()
        {
            return _permittedOps;
        }

        public String getStateType()
        {
            return name();
        }

        @Nullable
        public static StateType getType(String stateType)
        {
            for (StateType type : StateType.values())
            {
                if (type.getStateType().equals(stateType))
                    return type;
            }
            return null;
        }
    }

    /**
     * The operation that might be performed for an associated DataState
     */
    public enum DataOperation
    {
        analysis("included for any data or statistical analysis, curve fitting etc"),
        hitSelection("can mark data for hit selection");

        final String _description;

        DataOperation(String description)
        {
            _description = description;
        }
    }

    private PlateDataStateManager(){}

    public static PlateDataStateManager get()
    {
        return _instance;
    }

    public boolean isOperationPermitted(@Nullable DataState state, DataOperation operation)
    {
        if (state == null)
            return true;

        StateType stateType = StateType.getType(state.getStateType());
        return stateType != null && stateType.getPermittedOps().contains(operation);
    }

    /**
     * Ensure that all plate data states exist for the container.
     */
    public void ensureDefaultStates(Container container, User user)
    {
        Container c = getDataStateContainer(container);
        Map<String, DataState> dataStates = DataStateManager.getInstance().getStates(c)
                .stream()
                .collect(Collectors.toMap(DataState::getLabel, k -> k));
        Set<String> typeNames = getStates(c).stream().map(DataState::getStateType).collect(Collectors.toSet());

        for (StateType type : StateType.values())
        {
            if (typeNames.contains(type.getStateType()))
                continue;

            DataState existing = dataStates.get(type.name());
            if (existing == null)
            {
                createDataState(c, user, type.name(), type.getStateType(), type._description);
            }
            else if (!type.getStateType().equals(existing.getStateType()))
            {
                // name matches but not the required state type, try generating a unique name
                int i = 1;
                String newName = type.name();
                while (dataStates.containsKey(newName))
                {
                    newName = String.format("%s (%d)", type.name(), i++);
                }
                createDataState(c, user, newName, type.getStateType(), type._description);
            }
        }
    }

    private void createDataState(Container c, User user, String name, String stateType, String description)
    {
        DataState state = new DataState();
        state.setContainer(c);
        state.setLabel(name);
        state.setStateType(stateType);
        state.setDescription(description);

        DataStateManager.getInstance().insertState(user, state);
    }

    @Nullable
    public DataState getStateForRowId(Container container, Long rowId)
    {
        return DataStateManager.getInstance().getStateForRowId(getDataStateContainer(container), rowId);
    }

    private Container getDataStateContainer(Container container)
    {
        // scope the data state container to the project
        if (container.isRoot())
            return container;
        return container.isProject() ? container : container.getProject();
    }

    @Override
    public String getHandlerType()
    {
        return HANDLER_NAME;
    }

    @Override
    public List<DataState> getStates(Container container)
    {
        return DataStateManager.getInstance().getStates(getDataStateContainer(container)).stream()
                .filter(state -> StateType.getType(state.getStateType()) != null)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isStateInUse(Container container, DataState state)
    {
        // for now, we won't allow removal of the default plate data states
        return StateType.getType(state.getStateType()) != null;
    }

    @Override
    public boolean isBlankStatePublic(Container container)
    {
        return false;
    }

    @Override
    public boolean isRequireCommentOnQCStateChange(Container container)
    {
        return false;
    }

    @Override
    public @Nullable String getStateChangeError(Container container, DataState state, Map rowUpdates)
    {
        // no changes allowed for now
        if (StateType.getType(state.getStateType()) != null)
            return "Data State for '" + state.getLabel() + "' cannot be changed.";

        return null;
    }

    @Override
    public void updateState(Container container, AbstractManageDataStatesForm form, User user)
    {
        // nothing to do, this state handler isn't on the end of the manage action
    }
}
