package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.model.ParticipantGroupService;
import org.labkey.api.study.model.ParticipantGroup;

import java.util.Collection;

public class ParticipantGroupServiceImpl implements ParticipantGroupService
{
    @Override
    public ParticipantGroup getParticipantGroup(Container container, User user, int rowId)
    {
        return ParticipantGroupManager.getInstance().getParticipantGroup(container, user, rowId);
    }

    @Override
    public Collection<String> getParticipantIdsForGroup(Study study, User user, int groupId)
    {
        return StudyManager.getInstance().getParticipantIdsForGroup(study, user, groupId);
    }
}
