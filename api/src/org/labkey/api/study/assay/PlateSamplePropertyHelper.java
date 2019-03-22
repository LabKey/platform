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

import org.labkey.api.exp.SamplePropertyHelper;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.study.WellGroup;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * User: jeckels
 * Date: Oct 3, 2007
 */
public class PlateSamplePropertyHelper extends SamplePropertyHelper<String>
{
    protected List<String> _sampleNames;
    protected final PlateTemplate _template;
    protected WellGroup.Type _wellgroupType;

    public PlateSamplePropertyHelper(List<? extends DomainProperty> domainProperties, PlateTemplate template)
    {
        this(domainProperties, template, WellGroup.Type.SPECIMEN);
    }

    public PlateSamplePropertyHelper(List<? extends DomainProperty> domainProperties, PlateTemplate template, WellGroup.Type wellgroupType)
    {
        super(domainProperties);
        _template = template;
        _sampleNames = new ArrayList<>();
        _wellgroupType = wellgroupType;

        if (template != null)
        {
            for (WellGroupTemplate wellgroup : template.getWellGroups())
            {
                if (wellgroup.getType() == _wellgroupType)
                {
                    _sampleNames.add(wellgroup.getName());
                }
            }
        }
    }

    protected List<WellGroupTemplate> getSampleWellGroups()
    {
        List<WellGroupTemplate> samples = new ArrayList<>();
        for (WellGroupTemplate wellgroup : _template.getWellGroups())
        {
            if (wellgroup.getType() == _wellgroupType)
            {
                samples.add(wellgroup);
            }
        }
        return samples;
    }

    protected Map<String, WellGroupTemplate> getSampleWellGroupNameMap()
    {
        List<WellGroupTemplate> sampleGroups = getSampleWellGroups();
        Map<String, WellGroupTemplate> sampleGroupNames = new HashMap<>(sampleGroups.size());
        for (WellGroupTemplate sampleGroup : sampleGroups)
            sampleGroupNames.put(sampleGroup.getName(), sampleGroup);
        return sampleGroupNames;
    }

    protected String getObject(int index, Map<DomainProperty, String> sampleProperties)
    {
        List<WellGroupTemplate> samples = getSampleWellGroups();
        if (index >= samples.size())
            throw new IndexOutOfBoundsException("Requested #" + index + " but there were only " + samples.size() + " well group templates");
        return samples.get(index).getName();
    }

    protected boolean isCopyable(DomainProperty pd)
    {
        return !AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(pd.getName()) && !AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(pd.getName());
    }

    public List<String> getSampleNames()
    {
        return _sampleNames;
    }
}
