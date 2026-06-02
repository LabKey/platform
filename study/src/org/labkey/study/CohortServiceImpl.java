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
package org.labkey.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Study;
import org.labkey.api.study.model.CohortService;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.CohortManager;
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

    @Override
    public Cohort getCohortByLabel(Container container, User user, String label)
    {
        return StudyManager.getInstance().getCohortByLabel(container, user, label);
    }

    @Override
    public Cohort getCohortForRowId(Container container, User user, int rowId)
    {
        return StudyManager.getInstance().getCohortForRowId(container, user, rowId);
    }

    @Override
    public void deleteCohort(Cohort cohort)
    {
        if (cohort instanceof CohortImpl cohortImpl)
            StudyManager.getInstance().deleteCohort(cohortImpl);
    }

    @Override
    public Cohort updateCohort(Container container, User user, int rowId, String label, Integer subjectCount)
    {
        CohortImpl updatedCohort = StudyManager.getInstance().getCohortForRowId(container, user, rowId);
        if (updatedCohort != null)
        {
            updatedCohort = updatedCohort.createMutable();
            updatedCohort.setLabel(label);
            updatedCohort.setSubjectCount(subjectCount);
            StudyManager.getInstance().updateCohort(user, updatedCohort);
        }
        return updatedCohort;
    }

    @Override
    public Cohort createCohort(Study study, User user, String newLabel, boolean enrolled, Integer subjectCount, String description) throws ValidationException
    {
        return CohortManager.getInstance().createCohort(study, user, newLabel, enrolled, subjectCount, description);
    }
}
