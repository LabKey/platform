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

package org.labkey.study.requirements;

import org.labkey.study.SpecimenManager;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.SpecimenRequest;
import org.labkey.study.model.SpecimenRequestRequirement;
import org.labkey.study.model.Vial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
* Date: Jun 7, 2007
* Time: 11:26:49 AM
*/
public enum SpecimenRequestRequirementType implements RequirementType
{
    ORIGINATING_SITE
            {
                public List<SpecimenRequestRequirement> generateRequirements(SpecimenRequest owner, SpecimenRequestRequirement defaultRequirement)
                {
                    List<SpecimenRequestRequirement> requirements = new ArrayList<>();
                    List<Vial> vials = owner.getVials();
                    if (vials != null && vials.size() > 0)
                    {
                        // get a list of all providing and originating sites:
                        Set<Integer> originatingLocationIds = new HashSet<>();
                        for (Vial vial : vials)
                        {
                            LocationImpl originatingLocation = SpecimenManager.getInstance().getOriginatingLocation(vial);
                            if (originatingLocation != null)
                                originatingLocationIds.add(originatingLocation.getRowId());
                        }
                        for (Integer locationId : originatingLocationIds)
                        {
                            SpecimenRequestRequirement requirement = defaultRequirement.createMutable();
                            requirement.setSiteId(locationId);
                            requirement.setRequestId(owner.getRowId());
                            requirements.add(requirement);
                        }
                    }
                    return requirements;
                }
            },
    PROVIDING_SITE
            {
                public List<SpecimenRequestRequirement> generateRequirements(SpecimenRequest owner, SpecimenRequestRequirement defaultRequirement)
                {
                    List<SpecimenRequestRequirement> requirements = new ArrayList<>();
                    List<Vial> vials = owner.getVials();
                    if (vials != null && vials.size() > 0)
                    {
                        // get a list of all providing and originating sites:
                        Set<Integer> providerLocationIds = new HashSet<>();
                        for (Vial vial : vials)
                        {
                            LocationImpl providingLocation = SpecimenManager.getInstance().getCurrentLocation(vial);
                            if (providingLocation != null)
                                providerLocationIds.add(providingLocation.getRowId());
                        }
                        for (Integer locationId : providerLocationIds)
                        {
                            SpecimenRequestRequirement requirement = defaultRequirement.createMutable();
                            requirement.setRequestId(owner.getRowId());
                            requirement.setSiteId(locationId);
                            requirements.add(requirement);
                        }
                    }
                    return requirements;
                }
            },
    RECEIVING_SITE
            {
                public List<SpecimenRequestRequirement> generateRequirements(SpecimenRequest owner, SpecimenRequestRequirement defaultRequirement)
                {
                    if (owner.getDestinationSiteId() != null)
                    {
                        defaultRequirement.setSiteId(owner.getDestinationSiteId());
                        defaultRequirement.setRequestId(owner.getRowId());
                        return Collections.singletonList(defaultRequirement);
                    }
                    else
                        return Collections.emptyList();
                }
            },
    NON_SITE_BASED
            {
                public List<SpecimenRequestRequirement> generateRequirements(SpecimenRequest owner, SpecimenRequestRequirement defaultRequirement)
                {
                    defaultRequirement.setRequestId(owner.getRowId());
                    return Collections.singletonList(defaultRequirement);
                }
            };

    abstract public List<SpecimenRequestRequirement> generateRequirements(SpecimenRequest owner, SpecimenRequestRequirement defaultRequirement);
}
