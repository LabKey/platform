package org.labkey.experiment.samples;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;

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

    @Override
    public boolean showStates(Container container)
    {
        return !getStates(container).isEmpty();
    }

    public boolean isOperationPermitted(Container container, Integer stateId, @NotNull SampleTypeService.SampleOperations operation)
    {
        if (!SampleTypeService.isSampleStatusEnabled())
            return true;

        if (stateId == null)
            return true;

        DataState status = getStateForRowId(container, stateId);
        return ExpSchema.SampleStateType.isOperationPermitted(status.getStateType(), operation);
    }

    public boolean isOperationPermitted(DataState status, @NotNull SampleTypeService.SampleOperations operation)
    {
        if (status == null)
            return true;
        return ExpSchema.SampleStateType.isOperationPermitted(status.getStateType(), operation);
    }
}
