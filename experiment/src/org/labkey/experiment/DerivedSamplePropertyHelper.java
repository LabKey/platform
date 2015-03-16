/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.property.DomainPropertyImpl;

import java.util.*;

/**
 * User: jeckels
 * Date: Oct 3, 2007
 */
public class DerivedSamplePropertyHelper extends SamplePropertyHelper<String>
{
    private final List<String> _names;
    private final Map<Integer, String> _lsids = new HashMap<>();
    private final ExpSampleSet _sampleSet;
    private final Container _container;
    private final User _user;

    public DerivedSamplePropertyHelper(ExpSampleSet sampleSet, int sampleCount, Container c, User user)
    {
        super(getPropertyDescriptors(sampleSet, c));
        _sampleSet = sampleSet;
        _container = c;
        _user = user;
        _names = new ArrayList<>();
        for (int i = 1; i <= sampleCount; i++)
        {
            _names.add("Output Sample " + i);
        }
    }

    public ExpSampleSet getSampleSet()
    {
        return _sampleSet;
    }

    public List<String> getSampleNames()
    {
        return _names;
    }

    protected String getObject(int index, Map<DomainProperty, String> sampleProperties) throws DuplicateMaterialException
    {
        String lsid = _lsids.get(index);
        if (lsid == null)
        {
            String name = determineMaterialName(sampleProperties);
            if (_sampleSet == null)
            {
                XarContext context = new XarContext("DeriveSamples", _container, _user);
                try
                {
                    lsid = LsidUtils.resolveLsidFromTemplate("${FolderLSIDBase}:" + name, context, "Material");
                }
                catch (XarFormatException e)
                {
                    // Shouldn't happen - our template is safe
                    throw new RuntimeException(e);
                }
            }
            else
            {
                lsid = _sampleSet.getMaterialLSIDPrefix() + name;
            }

            if (_lsids.containsValue(lsid) || ExperimentService.get().getExpMaterial(lsid) != null)
            {
                // Default to not showing on a particular column
                String colName = "main";
                if (!getNamePDs().isEmpty() && getSampleNames().size() > index)
                {
                    colName = UploadWizardAction.getInputName(getNamePDs().get(0), getSampleNames().get(index));
                }
                throw new DuplicateMaterialException("Duplicate material name: " + name, colName);
            }
            _lsids.put(index, lsid);
        }
        return lsid;
    }

    public String determineMaterialName(Map<DomainProperty, String> sampleProperties)
    {
        String separator = "";
        StringBuilder sb = new StringBuilder();
        for (DomainProperty pd : getNamePDs())
        {
            sb.append(separator);
            separator = "-";
            sb.append(sampleProperties.get(pd));
        }
        return sb.toString();
    }

    protected boolean isCopyable(DomainProperty pd)
    {
        return !getNamePDs().contains(pd);
    }

    public static List<? extends DomainProperty> getPropertyDescriptors(ExpSampleSet sampleSet, Container c)
    {
        List<DomainProperty> dps = new ArrayList<>();

        if (sampleSet != null && sampleSet.getType() != null)
        {
            dps.addAll(sampleSet.getType().getProperties());
        }
        else
        {
            PropertyDescriptor namePropertyDescriptor = new PropertyDescriptor(ExperimentServiceImpl.get().getTinfoMaterial().getColumn("Name"), c);
            namePropertyDescriptor.setRequired(true);
            DomainPropertyImpl nameDomainProperty = new DomainPropertyImpl(null, namePropertyDescriptor);
            dps.add(nameDomainProperty);
        }
        
        return dps;
    }

    public List<? extends DomainProperty> getNamePDs()
    {
        if (_sampleSet != null)
        {
            Set<String> idColNames = new HashSet<>();
            for (DomainProperty pd : _sampleSet.getIdCols())
                idColNames.add(pd.getName());
            List<DomainProperty> properties = new ArrayList<>();
            for (DomainProperty dp : _sampleSet.getType().getProperties())
            {
                if (idColNames.contains(dp.getName()))
                    properties.add(dp);
            }
            return properties;
        }
        else
        {
            assert _domainProperties.get(0).getName().equals("Name");
            return Collections.singletonList(_domainProperties.get(0));
        }
    }

}
