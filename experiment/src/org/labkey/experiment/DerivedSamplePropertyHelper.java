/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.actions.UploadWizardAction;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.exp.DuplicateMaterialException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.SamplePropertyHelper;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.security.User;
import org.labkey.experiment.api.ExpSampleSetImpl;
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
public class DerivedSamplePropertyHelper extends SamplePropertyHelper<Lsid>
{
    private final List<String> _names;
    private final Map<Integer, Lsid> _lsids = new HashMap<>();
    private final ExpSampleSetImpl _sampleSet;
    private final Container _container;
    private final User _user;

    private final DomainProperty _nameProperty;
    private final NameGenerator _nameGenerator;
    private NameGenerator.State _state;

    public DerivedSamplePropertyHelper(ExpSampleSetImpl sampleSet, int sampleCount, Container c, User user)
    {
        super(Collections.emptyList());

        _sampleSet = sampleSet;
        if (_sampleSet != null)
            _nameGenerator = _sampleSet.getNameGenerator();
        else
            _nameGenerator = null;

        _container = c;
        _user = user;
        _names = new ArrayList<>();
        for (int i = 1; i <= sampleCount; i++)
        {
            _names.add("Output Sample " + i);
        }

        PropertyDescriptor namePropertyDescriptor = new PropertyDescriptor(ExperimentServiceImpl.get().getTinfoMaterial().getColumn("Name"), c);
        namePropertyDescriptor.setRequired(!_sampleSet.hasNameExpression());
        _nameProperty = new DomainPropertyImpl(null, namePropertyDescriptor);

        List<DomainProperty> dps = new ArrayList<>();
        if (sampleSet != null)
        {
            if (sampleSet.hasNameAsIdCol())
            {
                dps.add(_nameProperty);
            }
            dps.addAll(sampleSet.getDomain().getProperties());
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

    @Override
    public List<String> getSampleNames()
    {
        return _names;
    }

    @Override
    protected Lsid getObject(int index, @NotNull Map<DomainProperty, String> sampleProperties, @NotNull Set<ExpMaterial> parentMaterials) throws DuplicateMaterialException
    {
        Lsid lsid = _lsids.get(index);
        if (lsid == null)
        {
            String name = determineMaterialName(sampleProperties, parentMaterials);
            if (_sampleSet == null)
            {
                XarContext context = new XarContext("DeriveSamples", _container, _user);
                try
                {
                    String lsidStr = LsidUtils.resolveLsidFromTemplate("${FolderLSIDBase}:" + name, context, ExpMaterial.DEFAULT_CPAS_TYPE);
                    lsid = Lsid.parse(lsidStr);
                }
                catch (XarFormatException e)
                {
                    // Shouldn't happen - our template is safe
                    throw new RuntimeException(e);
                }
            }
            else
            {
                lsid = _sampleSet.generateSampleLSID().setObjectId(name).build();
            }

            if (_lsids.containsValue(lsid) || ExperimentService.get().getExpMaterial(lsid.toString()) != null)
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

    public String determineMaterialName(Map<DomainProperty, String> sampleProperties, Set<ExpMaterial> parentSamples)
    {
        if (_sampleSet != null)
        {
            if (_state == null)
            {
                _state = _nameGenerator.createState(true);
            }

            Map<String, Object> context = new CaseInsensitiveHashMap<>();
            for (Map.Entry<DomainProperty, String> entry : sampleProperties.entrySet())
            {
                context.put(entry.getKey().getName(), entry.getValue());
            }
            try
            {
                return _nameGenerator.generateName(_state, context, null, parentSamples);
            }
            catch (NameGenerator.NameGenerationException e)
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

    @Override
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
            for (DomainProperty dp : _sampleSet.getDomain().getProperties())
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
