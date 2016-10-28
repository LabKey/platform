/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpPostRedirectView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.sql.ResultSet;
import java.sql.SQLException;
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

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

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

        public PublishBean(AssayProvider provider, ExpProtocol protocol,
                           List<Integer> ids, String dataRegionSelectionKey,
                           Set<Container> studies, boolean nullStudies, boolean insufficientPermissions, String returnURL,
                           String containerFilterName)
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
    }

    public ModelAndView getView(PublishForm publishForm, BindException errors) throws Exception
    {
        _protocol = publishForm.getProtocol();
        AssayProvider provider = publishForm.getProvider();

        final List<Integer> ids;
        AssayTableMetadata tableMetadata = provider.getTableMetadata(_protocol);
        if (publishForm.isRunIds())
        {
            // Need to convert the run ids into data row ids
            List<Integer> runIds = getCheckboxIds();
            DataRegionSelection.clearAll(getViewContext(), null);
            // Get the assay results table
            UserSchema schema = provider.createProtocolSchema(getUser(), getContainer(), _protocol, null);
            TableInfo table = schema.getTable(AssayProtocolSchema.DATA_TABLE_NAME);
            if (table.supportsContainerFilter() && publishForm.getContainerFilterName() != null)
            {
                ((ContainerFilterable)table).setContainerFilter(ContainerFilter.getContainerFilterByName(publishForm.getContainerFilterName(), getUser()));
            }
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

            new TableSelector(table, Arrays.asList(dataRowIdColumn, runIdColumn), filter, new Sort(runFieldKey.toString())).setForDisplay(true).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    ids.add(dataRowIdColumn.getIntValue(rs));
                }
            });
        }
        else
        {
            ids = getCheckboxIds();
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
            ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getCopyToStudyConfirmURL(getContainer(), _protocol);
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
                        publishForm.getContainerFilterName()));
        }
    }

    public NavTree appendNavTrail(NavTree root)
    {
        getPageConfig().setHelpTopic(new HelpTopic("publishAssayData"));
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        result.addChild("Copy to Study: Choose Target");
        return result;
    }
}
