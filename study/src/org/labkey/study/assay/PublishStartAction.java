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

package org.labkey.study.assay;

import org.labkey.api.assay.AssayQCService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.assay.actions.BaseAssayAction;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpPostRedirectView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:09:28 PM
*/
@RequiresPermission(InsertPermission.class)
public class PublishStartAction extends BaseAssayAction<PublishStartAction.PublishForm>
{
    private ExpProtocol _protocol;

    public static class PublishForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _dataRegionSelectionKey;
        private String _containerFilterName;
        private boolean _runIds;

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        @Override
        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public String getContainerFilterName()
        {
            return _containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            _containerFilterName = containerFilterName;
        }

        public boolean isRunIds()
        {
            return _runIds;
        }

        public void setRunIds(boolean runIds)
        {
            _runIds = runIds;
        }
    }

    public class PublishBean
    {
        private List<Integer> _ids;
        private AssayProvider _provider;
        private ExpProtocol _protocol;
        private Set<Container> _studies;
        private boolean _nullStudies;
        private boolean _insufficientPermissions;
        private String _dataRegionSelectionKey;
        private final String _returnURL;
        private final String _containerFilterName;
        private List<Integer> _runIds;

        public PublishBean(AssayProvider provider, ExpProtocol protocol,
                           List<Integer> ids, String dataRegionSelectionKey,
                           Set<Container> studies, boolean nullStudies, boolean insufficientPermissions, String returnURL,
                           String containerFilterName, List<Integer> runIds)
        {
            _insufficientPermissions = insufficientPermissions;
            _provider = provider;
            _protocol = protocol;
            _studies = studies;
            _nullStudies = nullStudies;
            _ids = ids;
            _dataRegionSelectionKey = dataRegionSelectionKey;
            _returnURL = returnURL;
            _containerFilterName = containerFilterName;
            _runIds = runIds;
        }

        public String getReturnURL()
        {
            if (_returnURL != null)
            {
                return _returnURL;
            }
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), getProtocol()).addParameter("clearDataRegionSelectionKey", getDataRegionSelectionKey()).toString();
        }

        public List<Integer> getIds()
        {
            return _ids;
        }
        
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public Set<Container> getStudies()
        {
            return _studies;
        }

        public boolean isNullStudies()
        {
            return _nullStudies;
        }

        public AssayProvider getProvider()
        {
            return _provider;
        }

        public ExpProtocol getProtocol()
        {
            return _protocol;
        }

        public boolean isInsufficientPermissions()
        {
            return _insufficientPermissions;
        }

        public String getContainerFilterName()
        {
            return _containerFilterName;
        }

        public List<Integer> getRunIds()
        {
            return _runIds;
        }
    }

    @Override
    public ModelAndView getView(PublishForm publishForm, BindException errors)
    {
        _protocol = publishForm.getProtocol();
        AssayProvider provider = publishForm.getProvider();

        final List<Integer> ids;
        List<Integer> runIds = new ArrayList<>();

        AssayTableMetadata tableMetadata = provider.getTableMetadata(_protocol);
        if (publishForm.isRunIds())
        {
            // Need to convert the run ids into data row ids
            runIds = getCheckboxIds(getViewContext());
            DataRegionSelection.clearAll(getViewContext(), null);
            // Get the assay results table
            UserSchema schema = provider.createProtocolSchema(getUser(), getContainer(), _protocol, null);
            ContainerFilter cf = null;
            if (publishForm.getContainerFilterName() != null)
                cf = ContainerFilter.getContainerFilterByName(publishForm.getContainerFilterName(), getContainer(), getUser());
            TableInfo table = schema.getTable(AssayProtocolSchema.DATA_TABLE_NAME, cf);
            final ColumnInfo dataRowIdColumn = QueryService.get().getColumns(table, Collections.singleton(tableMetadata.getResultRowIdFieldKey())).get(tableMetadata.getResultRowIdFieldKey());
            assert dataRowIdColumn  != null : "Could not find dataRowId column in assay results table";
            FieldKey runFieldKey = tableMetadata.getRunRowIdFieldKeyFromResults();
            ColumnInfo runIdColumn = QueryService.get().getColumns(table, Collections.singleton(runFieldKey)).get(runFieldKey);
            assert runIdColumn  != null : "Could not find runId column in assay results table";

            // Filter it to get only the rows from this set of runs
            SimpleFilter filter = new SimpleFilter();
            filter.addClause(new SimpleFilter.InClause(runFieldKey, runIds, true));

            // Pull out the data row ids
            ids = new ArrayList<>();

            new TableSelector(table, Arrays.asList(dataRowIdColumn, runIdColumn), filter, new Sort(runFieldKey.toString())).setForDisplay(true).forEach(rs -> ids.add(dataRowIdColumn.getIntValue(rs)));
        }
        else
        {
            ids = getCheckboxIds(getViewContext());
        }

        // if QC is enabled for this protocol, verify that the selected data has been approved, otherwise show an error
        if (!validateQCState(runIds, ids))
        {
            return new HtmlView("<span class='labkey-error'>QC checks failed. There are unapproved rows of data in the copy to study selection, " +
                    "please change your selection or request a QC Analyst to approve the run data.</span>");
        }

        // If the TargetStudy column is on the result domain, redirect past the choose target study page directly to the confirm page.
        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> pair = provider.findTargetStudyProperty(_protocol);
        if (pair != null && pair.first == ExpProtocol.AssayDomainTypes.Result)
        {
            Collection<Pair<String, String>> inputs = new ArrayList<>();
            inputs.add(Pair.of(QueryParam.containerFilterName.name(), publishForm.getContainerFilterName()));
            if (publishForm.getReturnUrl() != null)
                inputs.add(Pair.of(ActionURL.Param.returnUrl.name(), publishForm.getReturnUrl().toString()));
            inputs.add(Pair.of(DataRegionSelection.DATA_REGION_SELECTION_KEY, publishForm.getDataRegionSelectionKey()));
            for (Integer id : ids)
                inputs.add(Pair.of(DataRegion.SELECT_CHECKBOX_NAME, id.toString()));

            // Copy url parameters to hidden inputs
            ActionURL url = PageFlowUtil.urlProvider(StudyUrls.class).getCopyToStudyConfirmURL(getContainer(), _protocol);
            for (Pair<String, String> parameter : url.getParameters())
                inputs.add(parameter);

            url.deleteParameters();
            getPageConfig().setTemplate(PageConfig.Template.None);
            return new HttpPostRedirectView(url.toString(), inputs);
        }
        else
        {
            Set<Container> containers = new HashSet<>();
            boolean nullsFound = false;
            boolean insufficientPermissions = false;
            for (Integer id : ids)
            {
                Container studyContainer = provider.getAssociatedStudyContainer(_protocol, id);
                if (studyContainer == null)
                    nullsFound = true;
                else
                {
                    if (!studyContainer.hasPermission(getUser(), InsertPermission.class))
                        insufficientPermissions = true;
                    containers.add(studyContainer);
                }
            }

            return new JspView<>("/org/labkey/study/assay/view/publishChooseStudy.jsp",
                    new PublishBean(provider,
                        _protocol,
                        ids,
                        publishForm.getDataRegionSelectionKey(),
                        containers,
                        nullsFound,
                        insufficientPermissions,
                        publishForm.getReturnUrl(),
                        publishForm.getContainerFilterName(),
                        runIds));
        }
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        getPageConfig().setHelpTopic(new HelpTopic("publishAssayData"));
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        result.addChild("Copy to Study: Choose Target");
        return result;
    }

    /**
     * Determines whether any of the runs or data rows passed in have an unapproved QC state.
     * The protocol must support QC and have been configured for QC, otherwise it will always return
     * true.
     *
     * @return true if all runs or data are approved, else false
     */
    private boolean validateQCState(List<Integer> runIds, List<Integer> dataIds)
    {
        if (AssayQCService.getProvider().supportsQC())
        {
            try
            {
                if (!runIds.isEmpty())
                {
                    return AssayQCService.getProvider().getUnapprovedRuns(_protocol, runIds).isEmpty();
                }
                else
                {
                    return AssayQCService.getProvider().getUnapprovedData(_protocol, dataIds).isEmpty();
                }

            }
            catch (ExperimentException e)
            {
                throw new RuntimeException(e);
            }
        }
        return true;
    }
}
