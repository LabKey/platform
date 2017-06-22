/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.labkey.api.study.Study;

import java.util.Map;

/**
 * User: davebradlee
 * Date: 8/14/12
 * Time: 10:18 AM
 *
 * Encapsulates whether date shifting and alternate ids are requested
 * and the mapping from participantId to the date shift and alternateId
 */
public class ParticipantMapper
{
    private Map<String, StudyManager.ParticipantInfo> _participantInfoMap = null;
    private final boolean _isShiftDates;
    private final boolean _isAlternateIds;

    public ParticipantMapper(Study study, User user, boolean isShiftDates, boolean isAlternateIds)
    {
        _isShiftDates = isShiftDates;
        _isAlternateIds = isAlternateIds;
        if (isShiftDates || isAlternateIds)
        {
            // If we need alternateIds, ensure that all participants have ids
            if (isAlternateIds)
                StudyManager.getInstance().generateNeededAlternateParticipantIds(study, user);

            _participantInfoMap = StudyManager.getInstance().getParticipantInfos(study, user, isShiftDates, isAlternateIds);
        }
    }

    public ParticipantMapper(Study study, boolean isShiftDates, boolean isAlternateIds)
    {
        this(study, null, isShiftDates, isAlternateIds);
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
