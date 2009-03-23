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

package org.labkey.api.study.actions;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryService;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:09:28 PM
*/
@RequiresPermission(ACL.PERM_INSERT)
public class PublishStartAction extends BaseAssayAction<PublishStartAction.PublishForm>
{
    private ExpProtocol _protocol;

    public static class PublishForm extends ProtocolIdForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _dataRegionSelectionKey;
        private String _returnURL;
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

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
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
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), getProtocol()).addParameter("clearDataRegionSelectionKey", getDataRegionSelectionKey()).toString();
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
        _protocol = getProtocol(publishForm);
        AssayProvider provider = AssayService.get().getProvider(_protocol);

        List<Integer> ids;
        if (publishForm.isRunIds())
        {
            // Need to convert the run ids into data row ids
            List<Integer> runIds = getCheckboxIds(true);
            // Get the assay results table
            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getContainer(), AssayService.ASSAY_SCHEMA_NAME);
            TableInfo table = schema.getTable(AssayService.get().getResultsTableName(_protocol));
            if (table instanceof ContainerFilterable && publishForm.getContainerFilterName() != null)
            {
                ((ContainerFilterable)table).setContainerFilter(ContainerFilter.getContainerFilterByName(publishForm.getContainerFilterName(), getViewContext().getUser()));
            }
            ColumnInfo dataRowIdColumn = QueryService.get().getColumns(table, Collections.singleton(provider.getDataRowIdFieldKey())).get(provider.getDataRowIdFieldKey());
            assert dataRowIdColumn  != null : "Could not find dataRowId column in assay results table";
            ColumnInfo runIdColumn = QueryService.get().getColumns(table, Collections.singleton(provider.getRunIdFieldKeyFromDataRow())).get(provider.getRunIdFieldKeyFromDataRow());
            assert runIdColumn  != null : "Could not find runId column in assay results table";

            // Filter it to get only the rows from this set of runs
            SimpleFilter filter = new SimpleFilter();
            filter.addClause(new SimpleFilter.InClause(provider.getRunIdFieldKeyFromDataRow().toString(), runIds, true));

            ResultSet rs = Table.selectForDisplay(table, Arrays.asList(dataRowIdColumn, runIdColumn), filter, new Sort(provider.getRunIdFieldKeyFromDataRow().toString()), Table.ALL_ROWS, 0);
            try
            {
                // Pull out the data row ids
                ids = new ArrayList<Integer>();
                while (rs.next())
                {
                    ids.add(dataRowIdColumn.getIntValue(rs));
                }
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
            }
        }
        else
        {
            ids = getCheckboxIds(false);
        }

        Set<Container> containers = new HashSet<Container>();
        boolean nullsFound = false;
        boolean insufficientPermissions = false;
        for (Integer id : ids)
        {
            Container studyContainer = provider.getAssociatedStudyContainer(_protocol, id);
            if (studyContainer == null)
                nullsFound = true;
            else
            {
                if (!studyContainer.hasPermission(getViewContext().getUser(), ACL.PERM_INSERT))
                    insufficientPermissions = true;
                containers.add(studyContainer);
            }
        }

        return new JspView<PublishBean>("/org/labkey/study/assay/view/publishChooseStudy.jsp",
                new PublishBean(provider,
                    _protocol,
                    ids,
                    publishForm.getDataRegionSelectionKey(),
                    containers,
                    nullsFound,
                    insufficientPermissions,
                    publishForm.getReturnURL(),
                    publishForm.getContainerFilterName()));
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        result.addChild("Copy to Study: Choose Target");
        return result;
    }

    public AppBar getAppBar()
    {
        return getAppBar(_protocol);
    }
}
