package org.labkey.api.qc;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.query.ExpSchema;

import java.util.List;
import java.util.stream.Collectors;

public class SampleStateManager extends DataStateManager
{
    private static final SampleStateManager _instance = new SampleStateManager();

    private SampleStateManager() {}

    public static SampleStateManager getInstance()
    {
        return _instance;
    }

    @Override
    @NotNull
    public List<DataState> getStates(Container container)
    {
        List<DataState> allStates = super.getStates(container);
        return allStates.stream().filter(state -> {
            var typeName = state.getStateType();
            if (typeName == null)
                return false;
            try {
                ExpSchema.SampleStateType.valueOf(typeName);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }).collect(Collectors.toList());
    }

    public List<DataState> getAllProjectStates(Container container)
    {
        List<DataState> states = getStates(container);
        if (!container.isProject())
            states.addAll(getStates(container.getProject()));
        if (container != ContainerManager.getSharedContainer())
            states.addAll(getStates(ContainerManager.getSharedContainer()));
        return states;
    }

    public DataState getState(Container container, Integer stateId)
    {
        if (stateId == null)
            return null;

        List<DataState> allStates = getAllProjectStates(container);
        return allStates.stream().filter(state -> stateId.equals(state.getRowId())).findFirst().orElse(null);
    }

    @Override
    public boolean showStates(Container container)
    {
        return !getStates(container).isEmpty();
    }
}
