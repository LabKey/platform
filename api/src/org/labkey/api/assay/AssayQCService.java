package org.labkey.api.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.qc.QCState;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to assign a QC state to assay data. QC state will allow administrators to support a workflow where
 * data may not be visible unless it is set to a state where access is allowed. The initial implementation
 * will support run level granularity but could be expanded to data row level as well.
 */
public interface AssayQCService
{
    List<AssayQCService> _providers = new ArrayList<>();
    AssayQCService _defaultProvider = new DefaultQCService();

    static void registerProvider(AssayQCService provider)
    {
        if (_providers.isEmpty())
        {
            _providers.add(provider);
        }
        else
            throw new RuntimeException("An implementation of AssayQCService has previously been registered");
    }

    @NotNull
    static AssayQCService getProvider()
    {
        if (!_providers.isEmpty())
        {
            return _providers.get(0);
        }
        return _defaultProvider;
    }

    /**
     * Return whether this provider supports assay QC functionality. Can be used to enable/disable UI elements
     * based on the presence of a valid QC provider.
     */
    boolean supportsQC();

    /**
     * Update the QC states for the specified runs
     */
    void setQCStates(ExpProtocol protocol, Container container, User user, List<Integer> runIds, QCState state, String comment);

    /**
     * Get the QC state (if any) associated with the run.
     */
    @Nullable
    QCState getQCState(ExpProtocol protocol, int runId);

    /**
     * Generate the filter condition for the runs table based on the configured QC state.
     */
    SQLFragment getRunsTableCondition(ExpProtocol protocol, Container container, User user);

    /**
     * Generate the filter condition for the data table based on the configured QC state.
     */
    SQLFragment getDataTableCondition(ExpProtocol protocol, Container container, User user);

    class DefaultQCService implements AssayQCService
    {
        @Override
        public boolean supportsQC()
        {
            return false;
        }

        @Override
        public void setQCStates(ExpProtocol protocol, Container container, User user, List<Integer> runIds, QCState state, String comment)
        {
        }

        @Nullable
        @Override
        public QCState getQCState(ExpProtocol protocol, int runId)
        {
            return null;
        }

        @Override
        public SQLFragment getRunsTableCondition(ExpProtocol protocol, Container container, User user)
        {
            return new SQLFragment();
        }

        @Override
        public SQLFragment getDataTableCondition(ExpProtocol protocol, Container container, User user)
        {
            return new SQLFragment();
        }
    }
}
