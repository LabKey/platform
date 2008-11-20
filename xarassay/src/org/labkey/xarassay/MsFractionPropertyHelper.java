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

package org.labkey.xarassay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;

import java.util.*;
import java.io.File;

/**
 * User: peter@labkey.com
 * Date: Oct 3, 2007
 */
public class MsFractionPropertyHelper extends SamplePropertyHelper<File>
{
    private List<String> _names;
    private ArrayList<File>_files;
    private final ExpSampleSet _sampleSet;
    private final int _sampleCount;
    private final Container _container;
    private final User _user;


    public MsFractionPropertyHelper(ExpSampleSet sampleSet, ArrayList<File> files, Container c, User user)
    {
        super(getPropertyDescriptors(sampleSet, c));
        _sampleSet = sampleSet;
        _files = files;
        _sampleCount = files.size();
        _container = c;
        _user = user;
        _names = new ArrayList<String>();
        for (int i = 0; i < _sampleCount; i++)
        {
            String fName = files.get(i).getName();
            _names.add("Fraction - " + fName.substring(0, fName.lastIndexOf('.')));
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

    protected File getObject(int index, Map<PropertyDescriptor, String> sampleProperties) throws DuplicateMaterialException
    {
        return _files.get(index);
    }

    public String determineMaterialName(Map<PropertyDescriptor, String> sampleProperties)
    {
        String separator = "";
        StringBuilder sb = new StringBuilder();
        for (PropertyDescriptor pd : getNamePDs())
        {
            sb.append(separator);
            separator = "-";
            sb.append(sampleProperties.get(pd));
        }
        return sb.toString();
    }

    protected boolean isCopyable(PropertyDescriptor pd)
    {
        return !getNamePDs().contains(pd);
    }

    public static PropertyDescriptor[] getPropertyDescriptors(ExpSampleSet sampleSet, Container c)
    {
        List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();

        if (sampleSet != null && sampleSet.getType() != null)
        {
            for (DomainProperty domainProp : sampleSet.getType().getProperties())
            {
                pds.add(domainProp.getPropertyDescriptor());
            }
        }
        else
        {
            PropertyDescriptor namePD = new PropertyDescriptor(ExperimentService.get().getTinfoMaterial().getColumn("Name"), c);
            namePD.setRequired(true);
            pds.add(namePD);
        }

        return pds.toArray(new PropertyDescriptor[pds.size()]);
    }

    public List<PropertyDescriptor> getNamePDs()
    {
        if (_sampleSet != null)
        {
            return _sampleSet.getIdCols();
        }
        else
        {
            assert _propertyDescriptors[0].getName().equals("Name");
            return Collections.singletonList(_propertyDescriptors[0]);
        }
    }

}