package org.labkey.api.evaluation;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.DismissibleWarningMessage;

public interface EvaluationService
{
    static @NotNull EvaluationService get()
    {
        EvaluationService service = ServiceRegistry.get().getService(EvaluationService.class);
        return service == null?
            new DefaultEvaluationService():
            service;
    }

    static void setInstance(EvaluationService instance)
    {
        ServiceRegistry.get().registerService(EvaluationService.class, instance);
    }

    default DismissibleWarningMessage getExpirationMessage()
    {
        return null;
    }

    class DefaultEvaluationService implements EvaluationService
    {
    }
}
