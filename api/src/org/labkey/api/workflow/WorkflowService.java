package org.labkey.api.workflow;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.security.User;import org.labkey.api.services.ServiceRegistry;

import java.util.Map;

public interface WorkflowService
{
    enum WorkflowConfigs
    {
        ActionId,
        JobId,
    }

    enum ActionType
    {
        AssayImport("assay types", "Imported assay data"),
        DeriveSamples("derivation sample type parameters", "Derived samples"),
        AliquotSamples("aliquot sample type parameters", "Aliquot samples"),
        PoolSamples("pooling sample type parameters", "Pooled samples");

        private final String _inputDescription;
        private final String _auditMessage;

        ActionType(String inputDescription, String auditMessage)
        {
            _inputDescription = inputDescription;
            _auditMessage = auditMessage;
        }

        public String getInputDescription()
        {
            return _inputDescription;
        }

        public String getAuditMessage()
        {
            return _auditMessage;
        }
    }

    static void setInstance(WorkflowService impl)
    {
        ServiceRegistry.get().registerService(WorkflowService.class, impl);
    }

    static WorkflowService get()
    {
        return ServiceRegistry.get().getService(WorkflowService.class);
    }

    void populateConfigParams(Map<String, Object> provided, Map<Enum, Object> configParameters);

    void populateConfigParams(HttpServletRequest request, Map<Enum, Object> configParameters);
    void onActionComplete(@NotNull Container container, @NotNull User user, @NotNull Long actionId);
    void onActionComplete(@NotNull Container container, @NotNull User user, @NotNull Long taskId, @NotNull ActionType actionType);

    DataIteratorBuilder getSampleCreationDataIteratorBuilder(DataIteratorBuilder data, Container container, User user);

    DataIteratorBuilder getActionAuditDataIteratorBuilder(DataIteratorBuilder data, Container container, User user);
}
