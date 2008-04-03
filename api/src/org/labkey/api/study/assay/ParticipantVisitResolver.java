package org.labkey.api.study.assay;

import org.labkey.api.study.ParticipantVisit;

import java.util.Date;

/**
 * User: jeckels
 * Date: Sep 17, 2007
 */
public interface ParticipantVisitResolver
{
    ParticipantVisit resolve(String specimenID, String participantID, Double visitID, Date date);
}
