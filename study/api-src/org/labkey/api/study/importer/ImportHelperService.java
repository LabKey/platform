package org.labkey.api.study.importer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;

public interface ImportHelperService
{
    static ImportHelperService get()
    {
        return ServiceRegistry.get().getService(ImportHelperService.class);
    }

    static void setInstance(ImportHelperService impl)
    {
        ServiceRegistry.get().registerService(ImportHelperService.class, impl);
    }

    SequenceNumTranslator getSequenceNumTranslator(Study study);
    ParticipantIdTranslator getParticipantIdTranslator(Study study, User user) throws ValidationException;

    interface SequenceNumTranslator
    {
        Double translateSequenceNum(@Nullable Object seq, @Nullable Object d) throws ValidationException;
    }

    interface ParticipantIdTranslator
    {
        String translateParticipantId(@Nullable Object p) throws ValidationException;
    }
}
