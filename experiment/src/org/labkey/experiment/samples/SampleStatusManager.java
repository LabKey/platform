package org.labkey.experiment.samples;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
import org.labkey.experiment.ExperimentModule;

import java.util.List;
import java.util.stream.Collectors;

public class SampleStatusManager extends DataStateManager
{
    private static final SampleStatusManager _instance = new SampleStatusManager();

    private SampleStatusManager() {}

    public static SampleStatusManager getInstance()
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
                ExpSchema.SampleStatusType.valueOf(typeName);
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

    public boolean isOperationPermitted(Container container, Integer stateId, @NotNull ExperimentService.SampleOperations operation)
    {
        if (!ExperimentModule.isSampleStatusEnabled())
            return true;

        if (stateId == null)
            return true;

        DataState status = getStateForRowId(container, stateId);
        return ExpSchema.SampleStatusType.isOperationPermitted(status.getStateType(), operation);
    }

    public boolean isOperationPermitted(DataState status, @NotNull ExperimentService.SampleOperations operation)
    {
        if (status == null)
            return true;
        return ExpSchema.SampleStatusType.isOperationPermitted(status.getStateType(), operation);
    }
}
