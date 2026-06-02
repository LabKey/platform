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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Study;
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

    // Methods created to support Vaccine study designs
    Cohort getCohortByLabel(Container container, User user, String label);

    Cohort getCohortForRowId(Container container, User user, int rowId);

    void deleteCohort(Cohort cohort);

    Cohort updateCohort(Container container, User user, int rowId, String label, Integer subjectCount);

    Cohort createCohort(Study study, User user, String newLabel, boolean enrolled, Integer subjectCount, String description) throws ValidationException;
}
