package org.labkey.api.study;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.annotations.Migrate;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.study.model.ParticipantInfo;
import org.springframework.validation.BindException;

import java.util.Map;

/**
 * Provides access to internal study methods for modules that depend on study and are aware of study internals
 * (for example, specimen)
 */
public interface StudyInternalService
{
    static StudyInternalService get()
    {
        return ServiceRegistry.get().getService(StudyInternalService.class);
    }

    static void setInstance(StudyInternalService impl)
    {
        ServiceRegistry.get().registerService(StudyInternalService.class, impl);
    }

    /**
     * Clears all the study caches in this container plus those of any associated ancillary/published studies. Does not
     * clear caches associated with datasets.
     * @param container The study container where cache clearing will take place
     */
    void clearCaches(Container container);

    Map<String, ParticipantInfo> getParticipantInfos(Study study, User user, boolean isShiftDates, boolean isAlternateIds);

    void generateNeededAlternateParticipantIds(Study study, User user);

    @Migrate
    void sendNewRequestNotifications(SpringActionController controller, SpecimenRequest request, BindException errors) throws Exception;

    void setLastSpecimenRequest(Study study, Integer lastSpecimenRequest);
}
