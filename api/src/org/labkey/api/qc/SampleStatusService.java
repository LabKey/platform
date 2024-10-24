package org.labkey.api.qc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to assign a status/state to samples. Sample states can be created for a few different types which will
 * allow for certain permitted operations based on that state (i.e. Available, Consumed, Locked). The default
 * implementation/provider will permit all operations for all samples regardless of their status value.
 */
public interface SampleStatusService
{
    List<SampleStatusService> _providers = new ArrayList<>();
    SampleStatusService _defaultProvider = new DefaultSampleStatusService();

    static void registerProvider(SampleStatusService provider)
    {
        if (_providers.isEmpty())
            _providers.add(provider);
        else
            throw new RuntimeException("An implementation of SampleStatusService has previously been registered.");
    }

    @NotNull
    static SampleStatusService get()
    {
        if (!_providers.isEmpty())
            return _providers.get(0);

        return _defaultProvider;
    }

    /**
     * Return whether this provider supports sample status functionality. Can be used to enable/disable UI elements
     * based on the presence of a valid sample status provider.
     */
    boolean supportsSampleStatus();

    @NotNull List<DataState> getStates(Container container);

    @NotNull List<DataState> getAllProjectStates(Container container);

    DataState getStateForRowId(Container container, Integer stateId);

    boolean isOperationPermitted(Container container, Integer stateId, @NotNull SampleTypeService.SampleOperations operation);

    boolean isOperationPermitted(DataState status, @NotNull SampleTypeService.SampleOperations operation);

    /**
     * Ensure default sample states are added to any container with the SM module enabled
     * that does not have any core.DataStates rows already defined.
     */
    void ensureDefaultStates(Container container, User user);

    /**
     * Determine if any samples are assigned the specified state
     */
    boolean isSampleStateInUse(DataState state);

    @Nullable ActionURL getManageSampleStatusesURL(Container container);

    class DefaultSampleStatusService implements SampleStatusService
    {
        @Override
        public boolean supportsSampleStatus()
        {
            return false;
        }

        @Override
        public @NotNull List<DataState> getStates(Container container)
        {
            return SampleStateManager.getInstance().getStates(container);
        }

        @Override
        public @NotNull List<DataState> getAllProjectStates(Container container)
        {
            return SampleStateManager.getInstance().getAllProjectStates(container);
        }

        @Override
        public DataState getStateForRowId(Container container, Integer stateId)
        {
            return SampleStateManager.getInstance().getState(container, stateId);
        }

        @Override
        public boolean isOperationPermitted(Container container, Integer stateId, SampleTypeService.@NotNull SampleOperations operation)
        {
            // by default all operations are permitted
            return true;
        }

        @Override
        public boolean isOperationPermitted(DataState status, SampleTypeService.@NotNull SampleOperations operation)
        {
            // by default all operations are permitted
            return true;
        }

        @Override
        public void ensureDefaultStates(Container container, User user)
        {
            // no-op
        }

        @Override
        public boolean isSampleStateInUse(DataState state)
        {
            return false;
        }

        @Override
        public @Nullable ActionURL getManageSampleStatusesURL(Container container)
        {
            return null;
        }
    }
}
