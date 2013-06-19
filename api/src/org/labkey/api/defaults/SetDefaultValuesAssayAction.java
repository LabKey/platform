/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.study.actions.ParticipantVisitResolverChooser;
import org.labkey.api.study.actions.StudyPickerColumn;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
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

        protected boolean renderResolverSubSelectors()
        {
            return false;
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

    protected DataRegion createDataRegion()
    {
        return new AssayDefaultValueDataRegion(_provider);
    }

}