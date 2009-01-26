/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.view.InsertView;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * User: jeckels
 * Date: Oct 3, 2007
 */
public abstract class SamplePropertyHelper<ObjectType>
{
    protected final DomainProperty[] _domainProperties;
    private Map<DomainProperty, DisplayColumnGroup> _groups;

    public SamplePropertyHelper(DomainProperty[] domainProperties)
    {
        _domainProperties = domainProperties;
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
            for (DomainProperty property : _domainProperties)
            {
                String inputName = UploadWizardAction.getInputName(property, names.get(i));
                sampleProperties.put(property, request.getParameter(inputName));
            }
            result.put(getObject(i, sampleProperties), sampleProperties);
        }
        return result;
    }

    public void addSampleColumns(InsertView view, User user)
    {
        addSampleColumns(view, user, null, false);
    }

    public void addSampleColumns(InsertView view, User user, AssayRunUploadContext defaultValueContext, boolean reshow)
    {
        DataRegion region = view.getDataRegion();
        List<String> sampleNames = getSampleNames();
        if (sampleNames.isEmpty())
            return;
        _groups = new HashMap<DomainProperty, DisplayColumnGroup>();
        Map<String, Map<DomainProperty, String>> domains = new HashMap<String, Map<DomainProperty, String>>();
        for (DomainProperty sampleProperty : _domainProperties)
        {
            List<DisplayColumn> cols = new ArrayList<DisplayColumn>();
            for (String name : getSampleNames())
            {
                String inputName = UploadWizardAction.getInputName(sampleProperty, name);
                if (!reshow && defaultValueContext != null)
                {
                    // get the map of default values that corresponds to our current sample:
                    String defaultValueKey = name + "_" + sampleProperty.getDomain().getName();
                    Map<DomainProperty, String> defaultValues = domains.get(defaultValueKey);
                    if (defaultValues == null)
                    {
                        defaultValues = defaultValueContext.getDefaultValues(sampleProperty.getDomain(), null, name);
                        domains.put(defaultValueKey,  defaultValues);
                    }
                    view.setInitialValue(inputName, defaultValues.get(sampleProperty));
                }
                ColumnInfo col = sampleProperty.getPropertyDescriptor().createColumnInfo(OntologyManager.getTinfoObject(), "ObjectURI", user);
                col.setName(inputName);
                cols.add(new SpecimenInputColumn(col));
            }
            DisplayColumnGroup group = new DisplayColumnGroup(cols, sampleProperty.getName(), isCopyable(sampleProperty));
            _groups.put(sampleProperty, group);
            region.addGroup(group);
        }
        if (reshow)
            view.setInitialValues(defaultValueContext.getRequest().getParameterMap());
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
            for (DomainProperty sampleProperty : _domainProperties)
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
