package org.labkey.study.model;

import org.labkey.api.study.Study;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: davebradlee
 * Date: 8/14/12
 * Time: 10:18 AM
 * To change this template use File | Settings | File Templates.
 *
 * Encapsulates whether date shifting and alternate ids are requested
 * and the mapping from participantId to the date shift and alternateId
 */
public class ParticipantMapper
{
    private Map<String, StudyManager.ParticipantInfo> _participantInfoMap = null;
    private final boolean _isShiftDates;
    private final boolean _isAlternateIds;

    public ParticipantMapper(Study study, boolean isShiftDates, boolean isAlternateIds)
    {
        _isShiftDates = isShiftDates;
        _isAlternateIds = isAlternateIds;
        if (isShiftDates || isAlternateIds)
        {
            // If we need alternateIds, ensure that all participants have ids
            if (isAlternateIds)
                StudyManager.getInstance().generateNeededAlternateParticipantIds(study);

            _participantInfoMap = StudyManager.getInstance().getParticipantInfos(study, isShiftDates, isAlternateIds);
        }
    }

    public String getMappedParticipantId(String participantId)
    {
        if (_isAlternateIds && null != _participantInfoMap)
            return _participantInfoMap.get(participantId).getAlternateId();
        return participantId;
    }

    public int getDateOffsetForParticipant(String participantId)
    {
        if (_isShiftDates && null != _participantInfoMap)
            return _participantInfoMap.get(participantId).getDateOffset();
        return 0;
    }

    public boolean isShiftDates()
    {
        return _isShiftDates;
    }

    public boolean isAlternateIds()
    {
        return _isAlternateIds;
    }
}
