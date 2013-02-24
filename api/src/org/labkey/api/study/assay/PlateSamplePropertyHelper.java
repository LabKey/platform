/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * User: jeckels
 * Date: Oct 3, 2007
 */
public class PlateSamplePropertyHelper extends SamplePropertyHelper<String>
{
    private List<String> _sampleNames;
    protected final PlateTemplate _template;

    public PlateSamplePropertyHelper(DomainProperty[] domainProperties, PlateTemplate template)
    {
        super(domainProperties);
        _template = template;
        _sampleNames = new ArrayList<String>();

        if (template != null)
        {
            for (WellGroupTemplate wellgroup : template.getWellGroups())
            {
                if (wellgroup.getType() == WellGroup.Type.SPECIMEN)
                {
                    _sampleNames.add(wellgroup.getName());
                }
            }
        }
    }

    protected List<WellGroupTemplate> getSampleWellGroups()
    {
        List<WellGroupTemplate> samples = new ArrayList<WellGroupTemplate>();
        for (WellGroupTemplate wellgroup : _template.getWellGroups())
        {
            if (wellgroup.getType() == WellGroup.Type.SPECIMEN)
            {
                samples.add(wellgroup);
            }
        }
        return samples;
    }

    protected String getObject(int index, Map<DomainProperty, String> sampleProperties)
    {
        List<WellGroupTemplate> samples = getSampleWellGroups();
        if (index >= samples.size())
            throw new IndexOutOfBoundsException("Requested #" + index + " but there were only " + samples.size() + " well group templates");
        return getSampleWellGroups().get(index).getName();
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
