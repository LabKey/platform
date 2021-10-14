package org.labkey.api.qc;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.SampleTypeService;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to assign a QC state to sample data. Sample QC states can be created for a few different types which will
 * allow for certain permitted operations based on that state (i.e. Available, Consumed, Locked). The default
 * implementation/provider will permit all operations.
 */
public interface SampleQCStateService
{
    List<SampleQCStateService> _providers = new ArrayList<>();
    SampleQCStateService _defaultProvider = new DefaultQCStateService();

    static void registerProvider(SampleQCStateService provider)
    {
        if (_providers.isEmpty())
            _providers.add(provider);
        else
            throw new RuntimeException("An implementation of SampleQCStateService has previously been registered");
    }

    @NotNull
    static SampleQCStateService get()
    {
        if (!_providers.isEmpty())
            return _providers.get(0);

        return _defaultProvider;
    }

    /**
     * Return whether this provider supports sample QC status functionality. Can be used to enable/disable UI elements
     * based on the presence of a valid QC provider.
     */
    boolean supportsQC();

    @NotNull List<DataState> getStates(Container container);

    boolean isOperationPermitted(Container container, Integer stateId, @NotNull SampleTypeService.SampleOperations operation);

    boolean isOperationPermitted(DataState status, @NotNull SampleTypeService.SampleOperations operation);

    class DefaultQCStateService implements SampleQCStateService
    {
        @Override
        public boolean supportsQC() // TODO rename to not use QC
        {
            return false;
        }

        @Override
        public @NotNull List<DataState> getStates(Container container)
        {
            return SampleStateManager.getInstance().getStates(container);
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
    }
}
