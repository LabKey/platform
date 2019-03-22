/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.data.Container;
import org.jetbrains.annotations.NotNull;

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
    private final Container _runContainer;

    public ThawListFileResolver(ParticipantVisitResolver childResolver, Map<String, ParticipantVisit> aliases, Container runContainer)
    {
        _childResolver = childResolver;
        _aliases = aliases;
        _runContainer = runContainer;
    }

    @Override
    public Container getRunContainer()
    {
        return _runContainer;
    }

    @NotNull
    public ParticipantVisit resolve(String specimenID, String participantID, Double visitID, Date date, Container resultDomainTargetStudy) throws ExperimentException
    {
        ParticipantVisit values = _aliases.get(specimenID);
        if (values == null)
        {
            throw new ThawListResolverException("Can not resolve thaw list entry for specimenId: " + specimenID);
        }
        return _childResolver.resolve(values.getSpecimenID(), values.getParticipantID(), values.getVisitID(), date, resultDomainTargetStudy);
    }
}
