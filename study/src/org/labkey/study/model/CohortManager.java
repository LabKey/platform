/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.security.User;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: adam
 * Date: May 13, 2009
 * Time: 10:21:20 AM
 */
public class CohortManager
{
    private CohortManager()
    {
    }

    public static void updateAutomaticCohortAssignment(Study study, User user, Integer participantCohortDataSetId, String participantCohortProperty) throws SQLException
    {
        study = study.createMutable();

        study.setManualCohortAssignment(false);
        study.setParticipantCohortDataSetId(participantCohortDataSetId);
        study.setParticipantCohortProperty(participantCohortProperty);
        StudyManager.getInstance().updateStudy(user, study);
        StudyManager.getInstance().updateParticipantCohorts(user, study);
    }

    public static void updateManualCohortAssignment(Study study, User user, Map<String, Integer> p2c) throws SQLException
    {
        if (!study.isManualCohortAssignment())
        {
            study = study.createMutable();
            study.setManualCohortAssignment(true);
            StudyManager.getInstance().updateStudy(user, study);
        }

        Participant[] participants = StudyManager.getInstance().getParticipants(study);

        for (Participant p : participants)
        {
            Integer newCohortId = p2c.get(p.getParticipantId());

            if (!nullSafeEqual(newCohortId, p.getCohortId()))
            {
                if (newCohortId.intValue() == -1) // unassigned cohort
                    p.setCohortId(null);
                else
                    p.setCohortId(newCohortId);

                StudyManager.getInstance().updateParticipant(user, p);
            }
        }
    }


    // TODO: Check for null label here?
    public static Cohort createCohort(Study study, User user, String newLabel) throws ServletException, SQLException
    {
        Cohort cohort = new Cohort();

        // Check if there's a conflict
        Cohort existingCohort = StudyManager.getInstance().getCohortByLabel(study.getContainer(), user, newLabel);

        if (existingCohort != null)
            throw new ServletException("A cohort with the label '" + newLabel + "' already exists");

        cohort.setLabel(newLabel);

        StudyManager.getInstance().createCohort(study, user, cohort);

        return cohort;
    }


    private static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }
}
