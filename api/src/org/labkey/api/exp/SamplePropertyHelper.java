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

package org.labkey.api.exp;

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.study.actions.UploadWizardAction;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * User: jeckels
 * Date: Oct 3, 2007
 */
public abstract class SamplePropertyHelper<ObjectType>
{
    protected final DomainProperty[] _propertyDescriptors;
    private Map<DomainProperty, DisplayColumnGroup> _groups;

    public SamplePropertyHelper(DomainProperty[] propertyDescriptors)
    {
        _propertyDescriptors = propertyDescriptors;
    }

    public abstract List<String> getSampleNames();

    protected abstract ObjectType getObject(int index, Map<DomainProperty, String> sampleProperties) throws DuplicateMaterialException;

    protected abstract boolean isCopyable(DomainProperty pd);

    public Map<ObjectType, Map<DomainProperty, String>> getSampleProperties(HttpServletRequest request) throws DuplicateMaterialException
    {
        Map<ObjectType, Map<DomainProperty, String>> result = new LinkedHashMap<ObjectType, Map<DomainProperty, String>>();
        List<String> names = getSampleNames();
        for (int i = 0; i < names.size(); i++)
        {
            Map<DomainProperty, String> sampleProperties = new HashMap<DomainProperty, String>();
            for (DomainProperty property : _propertyDescriptors)
            {
                String inputName = UploadWizardAction.getInputName(property, names.get(i));
                sampleProperties.put(property, request.getParameter(inputName));
            }
            result.put(getObject(i, sampleProperties), sampleProperties);
        }
        return result;
    }

    public void addSampleColumns(DataRegion region, User user)
    {
        List<String> sampleNames = getSampleNames();
        if (sampleNames.isEmpty())
            return;
        _groups = new HashMap<DomainProperty, DisplayColumnGroup>();
        for (DomainProperty sampleProperty : _propertyDescriptors)
        {
            List<DisplayColumn> cols = new ArrayList<DisplayColumn>();
            for (String name : getSampleNames())
            {
                ColumnInfo col = sampleProperty.getPropertyDescriptor().createColumnInfo(OntologyManager.getTinfoObject(), "ObjectURI", user);
                col.setName(UploadWizardAction.getInputName(sampleProperty, name));
                cols.add(new SpecimenInputColumn(col));
            }
            DisplayColumnGroup group = new DisplayColumnGroup(cols, sampleProperty.getName(), isCopyable(sampleProperty));
            _groups.put(sampleProperty, group);
            region.addGroup(group);
        }
        region.setGroupHeadings(sampleNames);
        region.setHorizontalGroups(true);
    }

    public Map<DomainProperty, DisplayColumnGroup> getGroups()
    {
        return _groups;
    }

    public Map<String, Map<DomainProperty, String>> getPostedPropertyValues(HttpServletRequest request)
    {
        Map<String, Map<DomainProperty, String>> result = new HashMap<String, Map<DomainProperty, String>>();
        for (String sampleName : getSampleNames())
        {
            Map<DomainProperty, String> values = new HashMap<DomainProperty, String>();
            for (DomainProperty sampleProperty : _propertyDescriptors)
            {
                String name = UploadWizardAction.getInputName(sampleProperty, sampleName);
                String inputName = ColumnInfo.propNameFromName(name);
                values.put(sampleProperty, request.getParameter(inputName));
            }
            result.put(sampleName, values);
        }
        return result;
    }

    private class SpecimenInputColumn extends DataColumn
    {
        public SpecimenInputColumn(ColumnInfo col)
        {
            super(col);
        }

        public boolean isEditable()
        {
            return true;
        }

        protected Object getInputValue(RenderContext ctx)
        {
            TableViewForm viewForm = ctx.getForm();
            return viewForm.getStrings().get(getName());
        }
    }
}
