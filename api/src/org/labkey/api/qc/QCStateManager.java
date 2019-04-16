package org.labkey.api.qc;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;

public class QCStateManager
{
    private static final QCStateManager _instance = new QCStateManager();
    private final DatabaseCache<List<QCState>> _qcStateCache = new DatabaseCache<>(CoreSchema.getInstance().getScope(), 1000, "QCStates");

    private QCStateManager(){}

    public static QCStateManager getInstance()
    {
        return _instance;
    }

    @NotNull
    public List<QCState> getQCStates(Container container)
    {
        return _qcStateCache.get(container.getId(), container, (key, argument) -> {
            Container container1 = (Container) argument;
            SimpleFilter filter = SimpleFilter.createContainerFilter(container1);
            return Collections.unmodifiableList(new TableSelector(CoreSchema.getInstance().getTableInfoQCState(), filter, new Sort("Label")).getArrayList(QCState.class));
        });
    }

    public boolean showQCStates(Container container)
    {
        return !getQCStates(container).isEmpty();
    }

    public QCState insertQCState(User user, QCState state)
    {
        _qcStateCache.remove(state.getContainer().getId());
        QCState newState = Table.insert(user, CoreSchema.getInstance().getTableInfoQCState(), state);

        return newState;
    }

    public QCState updateQCState(User user, QCState state)
    {
        _qcStateCache.remove(state.getContainer().getId());
        return Table.update(user, CoreSchema.getInstance().getTableInfoQCState(), state, state.getRowId());
    }

    public boolean deleteQCState(QCState state)
    {
        List<QCState> preDeleteStates = getQCStates(state.getContainer());
        _qcStateCache.remove(state.getContainer().getId());
        Table.delete(CoreSchema.getInstance().getTableInfoQCState(), state.getRowId());

        // return whether this is the last QC states as it may matter for some clients
        return (preDeleteStates.size() == 1);
    }

    public QCState getQCStateForRowId(Container container, int rowId)
    {
        for (QCState state : getQCStates(container))
        {
            if (state.getRowId() == rowId && state.getContainer().equals(container))
                return state;
        }
        return null;
    }

    public void clearCache(Container c)
    {
        _qcStateCache.remove(c.getId());
    }
}
