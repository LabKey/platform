package org.labkey.api.study.model;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;

import java.util.List;

/**
 * Provides specimen module access to a few visit-related methods while keeping VisitImpl, StudyManager, and all
 * their dependencies in study-main
 */
public interface VisitService
{
    static VisitService get()
    {
        return ServiceRegistry.get().getService(VisitService.class);
    }

    static void setInstance(VisitService impl)
    {
        ServiceRegistry.get().registerService(VisitService.class, impl);
    }

    List<? extends Visit> getVisits(Study study, Visit.Order order);

    ValidationException updateParticipantVisitsWithCohortUpdate(Study study, User user, boolean failForUndefinedVisits, @Nullable Logger logger);

    /**
     * Updates this study's participant, visit, and participant visit tables. Also updates automatic cohort assignments.
     */
    ValidationException updateParticipantVisits(Study study, User user);
}
