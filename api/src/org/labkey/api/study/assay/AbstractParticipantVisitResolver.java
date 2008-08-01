/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.ParticipantVisit;

import java.util.Date;
import java.util.Map;
import java.util.HashMap;

/**
 * User: brittp
 * Date: Oct 2, 2007
 * Time: 2:54:26 PM
 */
public abstract class AbstractParticipantVisitResolver implements ParticipantVisitResolver
{
    private Container _runContainer;
    private Container _targetStudyContainer;
    private Map<ParticipantVisit, ParticipantVisit> _cache = new HashMap<ParticipantVisit, ParticipantVisit>();

    public AbstractParticipantVisitResolver(Container runContainer, Container targetStudyContainer)
    {
        _runContainer = runContainer;
        _targetStudyContainer = targetStudyContainer;
    }

    protected Container getTargetStudyContainer()
    {
        return _targetStudyContainer;
    }

    protected Container getRunContainer()
    {
        return _runContainer;
    }

    public ParticipantVisit resolve(String specimenID, String participantID, Double visitID, Date date)
    {
        specimenID = specimenID == null ? null : specimenID.trim();
        if (specimenID != null && specimenID.length() == 0)
        {
            specimenID = null;
        }
        participantID = participantID == null ? null : participantID.trim();
        if (participantID != null && participantID.length() == 0)
        {
            participantID = null;
        }
        ParticipantVisit cacheKey = new ParticipantVisitImpl(specimenID, participantID, visitID, date);
        ParticipantVisit result = _cache.get(cacheKey);
        if (result != null)
        {
            return result;
        }
        result = resolveParticipantVisit(specimenID, participantID, visitID, date);
        assert result != null;

        if (result.getMaterial() == null)
        {
            // the study couldn't find a good material, so we'll have to mock one up:
            result = new ParticipantVisitImpl(result.getSpecimenID(), result.getParticipantID(), result.getVisitID(), result.getDate());
            ((ParticipantVisitImpl)result).setMaterial(createDummyMaterial(_runContainer, _targetStudyContainer, result.getSpecimenID(),
                    result.getParticipantID(), result.getVisitID()));
        }
        _cache.put(cacheKey, result);
        return result;
    }

    public static ExpMaterial createDummyMaterial(Container runContainer, Container targetStudyContainer, String specimenID, String participantID, Double visitID)
    {
        String name = "Participant" + (participantID != null ? participantID : "Unknown") + "Visit" + (visitID != null ? visitID : "Unknown");
        String lsid = new Lsid("AssayRunMaterial", "Folder-" + runContainer.getRowId(), name).toString();
        ExpMaterial material = ExperimentService.get().getExpMaterial(lsid);
        if (material == null)
        {
            material = ExperimentService.get().createExpMaterial(runContainer, lsid, name);
        }
        return material;
    }

    protected abstract ParticipantVisit resolveParticipantVisit(String specimenID, String participantID, Double visitID, Date date);
}
