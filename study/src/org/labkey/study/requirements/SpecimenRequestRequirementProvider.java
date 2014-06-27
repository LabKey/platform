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

import org.labkey.api.data.TableInfo;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;

import java.util.List;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 4:29:27 PM
 */
public class SpecimenRequestRequirementProvider extends DefaultRequirementProvider<SpecimenRequestRequirement, SpecimenRequestActor>
{
    public SpecimenRequestRequirementProvider()
    {
        super(SpecimenRequestRequirement.class, SpecimenRequestActor.class);
    }

    public RequirementType[] getRequirementTypes()
    {
        return SpecimenRequestRequirementType.values();
    }

    protected String getOwnerEntityIdColumnName()
    {
        return "OwnerEntityId";
    }

    protected Object getPrimaryKeyValue(SpecimenRequestRequirement requirement)
    {
        return requirement.getRowId();
    }

    protected String getActorSortColumnName()
    {
        return "SortOrder";
    }

    protected TableInfo getActorTableInfo()
    {
        return StudySchema.getInstance().getTableInfoSampleRequestActor();
    }

    protected TableInfo getRequirementTableInfo()
    {
        return StudySchema.getInstance().getTableInfoSampleRequestRequirement();
    }

    protected SpecimenRequestRequirement createMutable(SpecimenRequestRequirement requirement)
    {
        return requirement.createMutable();
    }

    protected List<SpecimenRequestRequirement> generateRequirementsFromDefault(RequirementOwner owner, SpecimenRequestRequirement defaultRequirement, RequirementType type)
    {
        return ((SpecimenRequestRequirementType) type).generateRequirements((SpecimenRequest) owner, defaultRequirement);
    }
}
