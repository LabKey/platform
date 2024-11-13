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
    private static PlateDataStateManager _instance = new PlateDataStateManager();

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

        @Nullable
        static StateType getType(String typeName)
        {
            for (StateType type : StateType.values())
            {
                if (type.name().equals(typeName))
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
        analysis("included for any data or statistical analysis, curve fitting etc");

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

    public void ensureDefaultStates(Container container, User user)
    {
        Set<String> typeNames = getStates(container).stream().map(DataState::getStateType).collect(Collectors.toSet());
        for (StateType type : StateType.values())
        {
            if (!typeNames.contains(type.name()))
            {
                DataState state = new DataState();
                state.setContainer(container);
                state.setStateType(type.name());
                state.setLabel(type.name());
                state.setDescription(type._description);

                DataStateManager.getInstance().insertState(user, state);
            }
        }
    }

    @Override
    public String getHandlerType()
    {
        return HANDLER_NAME;
    }

    @Override
    public List<DataState> getStates(Container container)
    {
        return DataStateManager.getInstance().getStates(container).stream()
                .filter(state -> StateType.getType(state.getStateType()) != null)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isStateInUse(Container container, DataState state)
    {
        // for now, we won't allow removal of the default plate data states
        return true;
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
