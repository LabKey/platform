/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.model;

import org.labkey.api.data.AbstractParticipantCategory;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.model.ParticipantGroup;
import org.labkey.api.study.model.ParticipantGroupService;

import java.util.Collection;
import java.util.List;

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

    @Override
    public List<? extends AbstractParticipantCategory> getParticipantCategories(Container c, User user)
    {
        return ParticipantGroupManager.getInstance().getParticipantCategories(c, user);
    }
}
