/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.exp.SamplePropertyHelper;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.property.DomainProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlateSamplePropertyHelper extends SamplePropertyHelper<String>
{
    protected List<String> _sampleNames;
    protected final Plate _template;
    protected WellGroup.Type _wellgroupType;

    public PlateSamplePropertyHelper(List<? extends DomainProperty> domainProperties, Plate template)
    {
        this(domainProperties, template, WellGroup.Type.SPECIMEN);
    }

    public PlateSamplePropertyHelper(List<? extends DomainProperty> domainProperties, Plate template, WellGroup.Type wellgroupType)
    {
        super(domainProperties);
        _template = template;
        _sampleNames = new ArrayList<>();
        _wellgroupType = wellgroupType;

        if (template != null)
        {
            for (WellGroup wellgroup : template.getWellGroups())
            {
                if (wellgroup.getType() == _wellgroupType)
                {
                    _sampleNames.add(wellgroup.getName());
                }
            }
        }
    }

    protected List<WellGroup> getSampleWellGroups()
    {
        List<WellGroup> samples = new ArrayList<>();
        for (WellGroup wellgroup : _template.getWellGroups())
        {
            if (wellgroup.getType() == _wellgroupType)
            {
                samples.add(wellgroup);
            }
        }
        return samples;
    }

    protected Map<String, WellGroup> getSampleWellGroupNameMap()
    {
        List<WellGroup> sampleGroups = getSampleWellGroups();
        Map<String, WellGroup> sampleGroupNames = new HashMap<>(sampleGroups.size());
        for (WellGroup sampleGroup : sampleGroups)
            sampleGroupNames.put(sampleGroup.getName(), sampleGroup);
        return sampleGroupNames;
    }

    @Override
    protected String getObject(int index, @NotNull Map<DomainProperty, String> sampleProperties, @NotNull Set<ExpMaterial> parentMaterials)
    {
        List<WellGroup> samples = getSampleWellGroups();
        if (index >= samples.size())
            throw new IndexOutOfBoundsException("Requested #" + index + " but there were only " + samples.size() + " well group templates");
        return samples.get(index).getName();
    }

    @Override
    protected boolean isCopyable(DomainProperty pd)
    {
        return !AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(pd.getName()) && !AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(pd.getName());
    }

    @Override
    public List<String> getSampleNames()
    {
        return _sampleNames;
    }
}
