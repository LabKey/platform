package org.labkey.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.model.CohortService;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.StudyManager;

import java.util.Collection;
import java.util.stream.Collectors;

public class CohortServiceImpl implements CohortService
{
    @Override
    public @Nullable CohortFilter getFromURL(Container c, User user, ActionURL url)
    {
        return CohortFilterFactory.getFromURL(c, user, url);
    }

    @Override
    public @Nullable CohortFilter getFromURL(Container c, User user, ActionURL url, @Nullable String dataRegionName)
    {
        return CohortFilterFactory.getFromURL(c, user, url, dataRegionName);
    }

    @Override
    public CohortFilter getUnassignedCohortFilter()
    {
        return CohortFilterFactory.UNASSIGNED;
    }

    @Override
    public Cohort getCurrentCohortForParticipant(Container c, User user, String participantId)
    {
        return StudyManager.getInstance().getCurrentCohortForParticipant(c, user, participantId);
    }

    @Override
    public Collection<CohortFilter> getCohortFilters(CohortFilter.Type type, Container c, User user)
    {
        return StudyManager.getInstance().getCohorts(c, user).stream()
            .map(cohort->new SingleCohortFilter(type, cohort))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<? extends Cohort> getCohorts(Container container, User user)
    {
        return StudyManager.getInstance().getCohorts(container, user);
    }
}
