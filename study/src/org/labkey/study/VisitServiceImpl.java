package org.labkey.study;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.VisitService;
import org.labkey.study.model.StudyManager;

import java.util.Collections;
import java.util.List;

public class VisitServiceImpl implements VisitService
{
    @Override
    public List<? extends Visit> getVisits(Study study, Visit.Order order)
    {
        return StudyManager.getInstance().getVisits(study, order);
    }

    @Override
    public void updateParticipantVisitsWithCohortUpdate(Study study, User user, @Nullable Logger logger)
    {
        StudyManager.getInstance().getVisitManager(study).updateParticipantVisitsWithCohortUpdate(user, logger);
    }

    @Override
    public void updateParticipantVisits(Study study, User user)
    {
        StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.emptySet());
    }
}
