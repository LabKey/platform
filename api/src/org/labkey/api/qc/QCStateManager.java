package org.labkey.api.qc;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;

import java.util.List;
import java.util.stream.Collectors;

public class QCStateManager extends DataStateManager
{
    private static final QCStateManager _instance = new QCStateManager();

    private QCStateManager() {}

    public static QCStateManager getInstance()
    {
        return _instance;
    }

    @Override
    @NotNull
    public List<DataState> getStates(Container container)
    {
        List<DataState> allStates = super.getStates(container);
        return allStates.stream().filter(DataState::isQCState).collect(Collectors.toList());
    }

    @Override
    public boolean showStates(Container container)
    {
        return !getStates(container).isEmpty();
    }
}
