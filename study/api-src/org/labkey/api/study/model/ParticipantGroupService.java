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
package org.labkey.api.study.model;

import org.labkey.api.data.AbstractParticipantCategory;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;

import java.util.Collection;
import java.util.List;

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

    List<? extends AbstractParticipantCategory> getParticipantCategories(Container c, User user);
}
