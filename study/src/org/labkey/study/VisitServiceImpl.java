package org.labkey.study;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.VisitService;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

import java.math.BigDecimal;
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

    @Override
    public Visit createVisit(Study study, User user, @NotNull BigDecimal seqMin, String label, Visit.Type type)
    {
        return StudyManager.getInstance().createVisit(study, user, new VisitImpl(study.getContainer(), seqMin, label, type));
    }

    @Override
    public void deleteVisit(Study study, User user, Visit visit)
    {
        if (study instanceof StudyImpl studyImpl && visit instanceof VisitImpl visitImpl)
            StudyManager.getInstance().deleteVisit(studyImpl, visitImpl, user);
    }
}
