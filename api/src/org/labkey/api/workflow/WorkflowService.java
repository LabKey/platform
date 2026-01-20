package org.labkey.api.workflow;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;import org.labkey.api.security.User;import org.labkey.api.services.ServiceRegistry;

public interface WorkflowService
{
    enum ActionType
    {
        AssayImport("assay types", "Imported assay data");

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

    void onActionComplete(@NotNull Container container, @NotNull User user, @NotNull Long actionId);
    void onActionComplete(@NotNull Container container, @NotNull User user, @NotNull Long taskId, @NotNull ActionType actionType);
}
