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
