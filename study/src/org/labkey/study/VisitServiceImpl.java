package org.labkey.study;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.VisitService;
import org.labkey.study.model.StudyManager;

import java.util.Collection;
import java.util.Collections;

public class VisitServiceImpl implements VisitService
{
    @Override
    public Collection<? extends Visit> getVisits(Study study, Visit.Order order)
    {
        return StudyManager.getInstance().getVisits(study, order);
    }

    @Override
    public @NotNull ValidationException updateParticipantVisitsWithCohortUpdate(Study study, User user, boolean failForUndefinedVisits, @Nullable Logger logger)
    {
        return StudyManager.getInstance().getVisitManager(study).updateParticipantVisitsWithCohortUpdate(user, failForUndefinedVisits, logger);
    }

    @Override
    public @NotNull ValidationException updateParticipantVisits(Study study, User user)
    {
        return StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.emptySet());
    }
}
