/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.DuplicateMaterialException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.SamplePropertyHelper;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.property.DomainPropertyImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gets the sample-specific values from user-provided information when creating child samples from an existing set
 * of parents.
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

    private final DomainProperty _nameProperty;

    public DerivedSamplePropertyHelper(ExpSampleSet sampleSet, int sampleCount, Container c, User user)
    {
        super(Collections.emptyList());

        _sampleSet = sampleSet;
        _container = c;
        _user = user;
        _names = new ArrayList<>();
        for (int i = 1; i <= sampleCount; i++)
        {
            _names.add("Output Sample " + i);
        }

        PropertyDescriptor namePropertyDescriptor = new PropertyDescriptor(ExperimentServiceImpl.get().getTinfoMaterial().getColumn("Name"), c);
        namePropertyDescriptor.setRequired(true);
        _nameProperty = new DomainPropertyImpl(null, namePropertyDescriptor);

        List<DomainProperty> dps = new ArrayList<>();
        if (sampleSet != null)
        {
            if (sampleSet.hasNameAsIdCol())
            {
                dps.add(_nameProperty);
            }
            for (DomainProperty property : sampleSet.getType().getProperties())
            {
                dps.add(property);
            }
        }
        else
        {
            dps.add(_nameProperty);
        }
        setDomainProperties(Collections.unmodifiableList(dps));
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
        if (_sampleSet != null)
        {
            Map<String, Object> context = new CaseInsensitiveHashMap<>();
            for (Map.Entry<DomainProperty, String> entry : sampleProperties.entrySet())
            {
                context.put(entry.getKey().getName(), entry.getValue());
            }
            try
            {
                return _sampleSet.createSampleName(context);
            }
            catch (ExperimentException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            assert _domainProperties.get(0).getName().equals("Name");
            return sampleProperties.get(_nameProperty);
        }
    }

    protected boolean isCopyable(DomainProperty pd)
    {
        return !getNamePDs().contains(pd);
    }

    public List<? extends DomainProperty> getNamePDs()
    {
        if (_sampleSet != null)
        {
            if (_sampleSet.hasNameAsIdCol())
            {
                return Collections.singletonList(_nameProperty);
            }

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
