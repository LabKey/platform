package org.labkey.api.study.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.view.ActionURL;

import java.util.Collection;

/**
 * Provides specimen module access to a few cohort methods while keeping CohortFilterFactory and all its
 * dependencies in study-main
 */
public interface CohortService
{
    static CohortService get()
    {
        return ServiceRegistry.get().getService(CohortService.class);
    }

    static void setInstance(CohortService impl)
    {
        ServiceRegistry.get().registerService(CohortService.class, impl);
    }

    @Nullable CohortFilter getFromURL(Container c, User user, ActionURL url);

    @Nullable CohortFilter getFromURL(Container c, User user, ActionURL url, @Nullable String dataRegionName);

    CohortFilter getUnassignedCohortFilter();

    Cohort getCurrentCohortForParticipant(Container c, User user, String participantId);

    Collection<CohortFilter> getCohortFilters(CohortFilter.Type type, Container c, User user);

    Collection<? extends Cohort> getCohorts(Container container, User user);
}
