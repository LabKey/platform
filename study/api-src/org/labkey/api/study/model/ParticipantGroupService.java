package org.labkey.api.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;

import java.util.Collection;

/**
 * Provides specimen module access to a few participant group methods while keeping ParticipantGroupManager and all
 * its dependencies in study-main
 */
public interface ParticipantGroupService
{
    static ParticipantGroupService get()
    {
        return ServiceRegistry.get().getService(ParticipantGroupService.class);
    }

    static void setInstance(ParticipantGroupService impl)
    {
        ServiceRegistry.get().registerService(ParticipantGroupService.class, impl);
    }

    ParticipantGroup getParticipantGroup(Container container, User user, int rowId);

    Collection<String> getParticipantIdsForGroup(Study study, User user, int groupId);
}
