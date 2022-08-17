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

package org.labkey.api.exp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.actions.AssayRunUploadForm;
import org.labkey.api.assay.actions.UploadWizardAction;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnGroup;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.CompletionType;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.ViewServlet;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

/**
 * Helper for mapping user-specified property values to the desired {@link DomainProperty} collection.
 * User: jeckels
 * Date: Oct 3, 2007
 */
public abstract class SamplePropertyHelper<ObjectType>
{
    protected List<? extends DomainProperty> _domainProperties;
    private Map<DomainProperty, DisplayColumnGroup> _groups;

    public SamplePropertyHelper(List<? extends DomainProperty> domainProperties)
    {
        setDomainProperties(domainProperties);
    }

    public void setDomainProperties(List<? extends DomainProperty> domainProperties)
    {
        _domainProperties = domainProperties;
        // Force re-calculation of groups if domain properties change:
        _groups = null;
    }

    public abstract List<String> getSampleNames();

    protected abstract ObjectType getObject(int index, @NotNull Map<DomainProperty, String> sampleProperties, @NotNull Set<ExpMaterial> parentMaterials) throws DuplicateMaterialException;

    protected Pair<ObjectType, String> getObjectWithName(int index, @NotNull Map<DomainProperty, String> sampleProperties, @NotNull Set<ExpMaterial> parentMaterials) throws DuplicateMaterialException
    {
        return new Pair<>(getObject(index, sampleProperties, parentMaterials), null);
    }

    protected abstract boolean isCopyable(DomainProperty pd);

    public Map<ObjectType, Map<DomainProperty, String>> getSampleProperties(HttpServletRequest request) throws ExperimentException
    {
        Map<Pair<ObjectType, String>, Map<DomainProperty, String>> map = getSampleProperties(request, emptySet());
        Map<ObjectType, Map<DomainProperty, String>> result = new LinkedHashMap<>();
        for (Map.Entry<Pair<ObjectType, String>, Map<DomainProperty, String>> entry : map.entrySet())
        {
            result.put(entry.getKey().first, entry.getValue());
        }
        return result;
    }

    public Map<Pair<ObjectType, String>, Map<DomainProperty, String>> getSampleProperties(HttpServletRequest request, @NotNull Set<ExpMaterial> parentMaterials) throws ExperimentException
    {
        Map<Pair<ObjectType, String>, Map<DomainProperty, String>> result = new LinkedHashMap<>();
        List<String> names = getSampleNames();
        for (int i = 0; i < names.size(); i++)
        {
            Map<DomainProperty, String> sampleProperties = new HashMap<>();
            for (DomainProperty property : _domainProperties)
            {
                String inputName = UploadWizardAction.getInputName(property, names.get(i));
                // handle the hidden input field prefixed with FIELD_MARKER for checkboxes
                String value = request.getParameter(inputName);
                if (value == null && request.getParameter(SpringActionController.FIELD_MARKER + inputName) != null)
                    value = "0";
                sampleProperties.put(property, value);
            }
            result.put(getObjectWithName(i, sampleProperties, parentMaterials), sampleProperties);
        }
        return result;
    }

    public void addSampleColumns(InsertView view, User user)
    {
        try
        {
            addSampleColumns(view, user, null, false);
        }
        catch (ExperimentException e)
        {
            // experiment exception should never be thrown if defaultValueContext is not provided
            throw new RuntimeException(e);
        }
    }

    public void addSampleColumns(InsertView view, User user, @Nullable AssayRunUploadForm defaultValueContext, boolean errorReshow) throws ExperimentException
    {
        DataRegion region = view.getDataRegion();
        List<String> sampleNames = getSampleNames();
        if (sampleNames.isEmpty())
            return;

        region.addGroupTable();
        _groups = new HashMap<>();
        Map<String, Map<DomainProperty, Object>> domains = new HashMap<>();
        for (DomainProperty sampleProperty : _domainProperties)
        {
            List<DisplayColumn> cols = new ArrayList<>();
            for (String name : getSampleNames())
            {
                String inputName = UploadWizardAction.getInputName(sampleProperty, name);
                ActionURL autoCompletePrefix = null;
                if (defaultValueContext != null)
                {
                    // get the map of default values that corresponds to our current sample:
                    String defaultValueKey = name + "_" + sampleProperty.getDomain().getName();
                    Map<DomainProperty, Object> defaultValues = domains.get(defaultValueKey);
                    if (defaultValues == null)
                    {
                        defaultValues = defaultValueContext.getDefaultValues(sampleProperty.getDomain(), name);
                        domains.put(defaultValueKey,  defaultValues);
                    }
                    view.setInitialValue(inputName, defaultValues.get(sampleProperty));

                    Container targetStudy = null;
                    if (defaultValueContext.getTargetStudy() != null)
                        targetStudy = ContainerManager.getForId(defaultValueContext.getTargetStudy());
                    if (targetStudy != null && targetStudy.hasPermission(defaultValueContext.getUser(), ReadPermission.class))
                    {
                        CompletionType type = null;

                        switch (sampleProperty.getName())
                        {
                            case AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME -> type = CompletionType.ParticipantId;
                            case AbstractAssayProvider.SPECIMENID_PROPERTY_NAME -> type = CompletionType.SpecimenGlobalUniqueId;
                            case AbstractAssayProvider.VISITID_PROPERTY_NAME -> type = CompletionType.VisitId;
                        }

                        if (null != type)
                            autoCompletePrefix = PageFlowUtil.urlProvider(StudyUrls.class).getCompletionURL(targetStudy, type);
                    }
                }
                var col = sampleProperty.getPropertyDescriptor().createColumnInfo(OntologyManager.getTinfoObject(), "ObjectURI", user, view.getViewContext().getContainer());
                col.setName(inputName);
                cols.add(new SpecimenInputColumn(col, autoCompletePrefix));
            }
            DisplayColumnGroup group = new DisplayColumnGroup(cols, sampleProperty.getName(), isCopyable(sampleProperty));
            _groups.put(sampleProperty, group);
            region.addGroup(group);
        }
        if (errorReshow && defaultValueContext != null)
            view.setInitialValues(ViewServlet.adaptParameterMap(defaultValueContext.getRequest().getParameterMap()));

        // don't display the group heading if there is only a single group
        if (sampleNames.size() == 1)
            region.setGroupHeadings(Collections.emptyList());
        else
            region.setGroupHeadings(sampleNames);
        region.setHorizontalGroups(true);
    }

    public Map<DomainProperty, DisplayColumnGroup> getGroups()
    {
        return _groups;
    }

    public List<? extends DomainProperty> getDomainProperties()
    {
        return _domainProperties;
    }

    public Map<String, Map<DomainProperty, String>> getPostedPropertyValues(HttpServletRequest request) throws ExperimentException
    {
        Map<String, Map<DomainProperty, String>> result = new LinkedHashMap<>();
        for (String sampleName : getSampleNames())
        {
            Map<DomainProperty, String> values = new HashMap<>();
            for (DomainProperty sampleProperty : _domainProperties)
            {
                String name = UploadWizardAction.getInputName(sampleProperty, sampleName);
                String inputName = ColumnInfo.propNameFromName(name);
                // handle the hidden input field prefixed with FIELD_MARKER for checkboxes
                String value = request.getParameter(inputName);
                if (value == null && request.getParameter(SpringActionController.FIELD_MARKER + inputName) != null)
                    value = "0";
                values.put(sampleProperty, value);
            }
            result.put(sampleName, values);
        }
        return result;
    }

    private static class SpecimenInputColumn extends DataColumn
    {
        private final ActionURL _autoCompletePrefix;

        public SpecimenInputColumn(ColumnInfo col, ActionURL autoCompletePrefix)
        {
            super(col);
            _autoCompletePrefix = autoCompletePrefix;
        }

        @Override
        public boolean isEditable()
        {
            return true;
        }

        @Override
        protected Object getInputValue(RenderContext ctx)
        {
            TableViewForm viewForm = ctx.getForm();
            return viewForm.getStrings().get(getName());
        }

        @Override
        protected ActionURL getAutoCompleteURLPrefix()
        {
            return _autoCompletePrefix;
        }
    }
}
