/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.SpecimenService;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Date;

/**
 * User: jeckels
 * Date: Sep 17, 2007
 */
public class StudyParticipantVisitResolver extends AbstractParticipantVisitResolver
{
    public StudyParticipantVisitResolver(Container runContainer, Container targetStudyContainer)
    {
        super(runContainer, targetStudyContainer);
    }

    private ParticipantVisit mergeParticipantVisitInfo(ParticipantVisitImpl originalInfo, ParticipantVisit studyInfo)
    {
        if (studyInfo == null)
            return originalInfo;
        // Because the user can specify a subset of properties (specimen id and participant id, for example) while
        // leaving others blank (visit id, for example), we merge the "correct" data provided by the study into the
        // user-provided properties, using the study value when the user did not specify a value, but otherwise trusting
        // that the user knows best.  This allows the user to upload assay data for a study for which all specimens have
        // not yet been loaded into the system.
        return new ParticipantVisitImpl(
                originalInfo.getSpecimenID() == null ? studyInfo.getSpecimenID() : originalInfo.getSpecimenID(),
                originalInfo.getParticipantID() == null ? studyInfo.getParticipantID() : originalInfo.getParticipantID(),
                originalInfo.getVisitID() == null ? studyInfo.getVisitID() : originalInfo.getVisitID(),
                originalInfo.getDate() == null ? studyInfo.getDate() : originalInfo.getDate(),
                getRunContainer());
    }

    @NotNull
    protected ParticipantVisit resolveParticipantVisit(String specimenID, String participantID, Double visitID, Date date)
    {
        ParticipantVisitImpl originalInfo = new ParticipantVisitImpl(specimenID, participantID, visitID, date, getRunContainer());

        if (getTargetStudyContainer() != null)
        {
            try
            {
                if (specimenID != null)
                {
                    return mergeParticipantVisitInfo(originalInfo, SpecimenService.get().getSampleInfo(getTargetStudyContainer(), specimenID));
                }
                else if (participantID != null && (visitID != null))
                {
                    return mergeParticipantVisitInfo(originalInfo, SpecimenService.get().getSampleInfo(getTargetStudyContainer(), participantID, visitID));
                }
                else if (participantID != null && date != null)
                {
                    return mergeParticipantVisitInfo(originalInfo, SpecimenService.get().getSampleInfo(getTargetStudyContainer(), participantID, date));
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        return originalInfo;
    }
}