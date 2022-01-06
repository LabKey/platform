package org.labkey.api.qc;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
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

    @Override
    public boolean showStates(Container container)
    {
        return !getStates(container).isEmpty();
    }
}
