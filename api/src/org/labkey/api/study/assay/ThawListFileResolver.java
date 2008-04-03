package org.labkey.api.study.assay;

import org.labkey.api.study.ParticipantVisit;

import java.util.Date;
import java.util.Map;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public class ThawListFileResolver implements ParticipantVisitResolver
{
    private final ParticipantVisitResolver _childResolver;

    private Map<String, ParticipantVisit> _aliases;

    public ThawListFileResolver(ParticipantVisitResolver childResolver, Map<String, ParticipantVisit> aliases)
    {
        _childResolver = childResolver;
        _aliases = aliases;
    }

    public ParticipantVisit resolve(String specimenID, String participantID, Double visitID, Date date)
    {
        ParticipantVisit values = _aliases.get(specimenID);
        if (values == null)
        {
            return new ParticipantVisitImpl(null, null, null, null);
        }
        return _childResolver.resolve(values.getSpecimenID(), values.getParticipantID(), values.getVisitID(), date);
    }
}
