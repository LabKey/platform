package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.SpecimenService;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

    protected ParticipantVisit resolveParticipantVisit(String specimenID, String participantID, Double visitID, Date date)
    {
        ParticipantVisitImpl originalInfo = new ParticipantVisitImpl(specimenID, participantID, visitID, date);

        if (getTargetStudyContainer() != null)
        {
            try
            {
                if (participantID != null && (visitID != null))
                {
                    return SpecimenService.get().getSampleInfo(getTargetStudyContainer(), participantID, visitID);
                }
                else if (participantID != null && date != null)
                {
                    return SpecimenService.get().getSampleInfo(getTargetStudyContainer(), participantID, date);
                }
                else if (specimenID != null)
                {
                    return SpecimenService.get().getSampleInfo(getTargetStudyContainer(), specimenID);
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