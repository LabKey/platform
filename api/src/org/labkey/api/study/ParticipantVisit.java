package org.labkey.api.study;

import org.labkey.api.exp.api.ExpMaterial;

import java.util.Date;

/**
 * User: brittp
* Date: Oct 2, 2007
* Time: 3:44:03 PM
*/
public interface ParticipantVisit
{
    String getParticipantID();

    Double getVisitID();

    String getSpecimenID();

    Date getDate();

    ExpMaterial getMaterial();
}