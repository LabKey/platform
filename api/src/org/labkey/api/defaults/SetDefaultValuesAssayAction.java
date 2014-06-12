/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.defaults;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.study.actions.ParticipantVisitResolverChooser;
import org.labkey.api.study.actions.StudyPickerColumn;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.ThawListResolverType;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/*
 * User: brittp
 * Date: Mar 2, 2009
 * Time: 4:20:14 PM
 */

@RequiresPermissionClass(DesignAssayPermission.class)
public class SetDefaultValuesAssayAction extends SetDefaultValuesAction<SetDefaultValuesAssayAction.AssayDomainIdForm>
{
    private AssayProvider _provider;

    public SetDefaultValuesAssayAction()
    {
        super(SetDefaultValuesAssayAction.AssayDomainIdForm.class);
    }

    public static class AssayDomainIdForm extends DomainIdForm
    {
        private String _providerName;

        public String getProviderName()
        {
            return _providerName;
        }

        public void setProviderName(String providerName)
        {
            _providerName = providerName;
        }
    }

    private class DefaultStudyPickerColumn extends StudyPickerColumn implements DefaultableDisplayColumn
    {
        public DefaultStudyPickerColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        protected boolean isDisabledInput()
        {
            return false;
        }

        public DefaultValueType getDefaultValueType()
        {
            return getColumnInfo().getDefaultValueType();
        }

        public Class getJavaType()
        {
            return getColumnInfo().getJavaClass();
        }
    }

    private class DefaultParticipantVisitResolverChooser extends ParticipantVisitResolverChooser implements DefaultableDisplayColumn
    {
        private ColumnInfo _boundColumn;

        public DefaultParticipantVisitResolverChooser(String typeInputName, List<ParticipantVisitResolverType> resolvers, ColumnInfo boundColumn)
        {
            super(typeInputName, resolvers, null);
            _boundColumn = boundColumn;
        }

        @Override
        protected boolean isDisabledInput()
        {
            return false;
        }

        @Override
        protected RenderSubSelectors renderResolverSubSelectors(ParticipantVisitResolverType resolver)
        {
            return resolver instanceof ThawListResolverType ? RenderSubSelectors.PARTIAL : RenderSubSelectors.NONE;
        }

        public DefaultValueType getDefaultValueType()
        {
            return _boundColumn.getDefaultValueType();
        }

        public Class getJavaType()
        {
            return _boundColumn.getJavaClass();
        }
    }

    protected class AssayDefaultValueDataRegion extends SetDefaultValuesAction.DefaultValueDataRegion
    {
        private AssayProvider _provider;

        public AssayDefaultValueDataRegion(AssayProvider provider)
        {
            _provider = provider;
        }

        public List<DisplayColumn> getDisplayColumns()
        {
            List<DisplayColumn> columns = super.getDisplayColumns();
            List<DisplayColumn> newColumns = new ArrayList<>();
            for (DisplayColumn displayColumn : columns)
            {
                ColumnInfo column = displayColumn.getColumnInfo();
                if (column.getName().equals(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME))
                    newColumns.add(new DefaultStudyPickerColumn(column));
                else if (column.getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
                    newColumns.add(new DefaultParticipantVisitResolverChooser(displayColumn.getName(), _provider.getParticipantVisitResolverTypes(), column));
                else
                    newColumns.add(displayColumn);
            }
            return newColumns;
        }
    }

    @Override
    public ModelAndView getView(AssayDomainIdForm domainIdForm, boolean reshow, BindException errors) throws Exception
    {
        _provider = AssayService.get().getProvider(domainIdForm.getProviderName());
        if (_provider == null)
        {
            throw new NotFoundException("Could not find assay provider with name " + domainIdForm.getProviderName());
        }
        return super.getView(domainIdForm, reshow, errors);

    }

    @Override
    protected ActionURL buildSetInheritedDefaultsURL(Domain domain, AssayDomainIdForm domainIdForm)
    {
        ActionURL url = super.buildSetInheritedDefaultsURL(domain, domainIdForm);
        url.addParameter("providerName", domainIdForm.getProviderName());
        return url;
    }

    protected DataRegion createDataRegion()
    {
        return new AssayDefaultValueDataRegion(_provider);
    }

    private static final String STRING_VALUE_PROPERTY_NAME = "stringValue";

    @Override
    protected String encodePropertyValues(AssayDomainIdForm domainIdForm, String propName) throws IOException
    {
        String value = super.encodePropertyValues(domainIdForm, propName);
        if (propName.equalsIgnoreCase(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
        {
            Map<String, String> values = new LinkedHashMap<>();
            values.put(STRING_VALUE_PROPERTY_NAME, value);

            // We store additional ThawList settings as JSON property value pairs inside the default value for ParticipantVisitResolver
            for (Object parameter : Collections.list(domainIdForm.getRequest().getParameterNames()))
            {
                String name = parameter.toString();
                if (name.startsWith(ThawListResolverType.NAMESPACE_PREFIX) && !name.equalsIgnoreCase(ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME))
                {
                    String thawListValue = domainIdForm.getRequest().getParameter(name);
                    if (!thawListValue.isEmpty())
                        values.put(name, thawListValue);
                }
            }

            value = new ObjectMapper().writeValueAsString(values);
        }
        return value;
    }

    @Override
    protected void decodePropertyValues(Map<String, Object> formDefaults, String propName, String stringValue) throws IOException
    {
        if (propName.equalsIgnoreCase(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
        {
            // ParticipantVisitResolver default value may be stored as a simple string, or it may be JSON encoded. If JSON encoded, it may have
            // addition nested properties containing ThawList list settings.
            try
            {
                Map<String, String> decodedVals = new ObjectMapper().readValue(stringValue, Map.class);
                formDefaults.put(propName, decodedVals.remove(STRING_VALUE_PROPERTY_NAME));
                formDefaults.putAll(decodedVals);
            }
            catch (JsonProcessingException e)
            {
                formDefaults.put(propName, stringValue);
            }
        }
        else
            formDefaults.put(propName, stringValue);

    }
}