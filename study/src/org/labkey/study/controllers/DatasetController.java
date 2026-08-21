/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.study.controllers;

import org.apache.commons.lang3.Strings;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.EditDatasetRowForm;
import org.labkey.api.study.InsertUpdateAction;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.study.StudySchema;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;

/**
 * User: jgarms
 */
public class DatasetController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(DatasetController.class);

    public DatasetController()
    {
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermission(ReadPermission.class)
    public static class UpdateAction extends InsertUpdateAction<EditDatasetRowForm>
    {
        public UpdateAction()
        {
            super(EditDatasetRowForm.class);
        }

        @Override
        protected boolean isInsert()
        {
            return false;
        }

        @Override
        protected void addExtraNavTrail(NavTree root)
        {
            root.addChild("Update Dataset Entry");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class InsertAction extends InsertUpdateAction<EditDatasetRowForm>
    {
        public InsertAction()
        {
            super(EditDatasetRowForm.class);
        }

        @Override
        protected boolean isInsert()
        {
            return true;
        }

        @Override
        protected void addExtraNavTrail(NavTree root)
        {
            root.addChild("Insert " + _ds.getLabel());
        }
    }

    @RequiresPermission(CanSeeAuditLogPermission.class)
    public class DatasetAuditHistoryAction extends SimpleViewAction<DatasetAuditHistoryForm>
    {
        @Override
        public ModelAndView getView(DatasetAuditHistoryForm form, BindException errors)
        {
            int auditRowId = form.getAuditRowId();
            String comment = null;
            String oldRecord = null;
            String newRecord = null;
            Container eventContainer = null;

            VBox view = new VBox();

            // getAuditEvent() resolves against the current container's audit schema (default ContainerFilter.Current +
            // CanSeeAuditLog clause), so a foreign auditRowId resolves to null and cannot disclose a record in another folder.
            DatasetAuditProvider.DatasetAuditEvent event = AuditLogService.get().getAuditEvent(getUser(), DatasetAuditProvider.DATASET_AUDIT_EVENT, auditRowId);
            if (event != null)
            {
                comment = event.getComment();
                oldRecord = event.getOldRecordMap();
                newRecord = event.getNewRecordMap();
                eventContainer = event.getContainer();
            }

            Map<String,String> oldData = null;
            Map<String,String> newData = null;
            // If the record was deleted, newRecord will be null. Otherwise we might be able to find it
            if (newRecord != null)
            {
                newData = DatasetAuditProvider.decodeFromDataMap(newRecord);
            }

            if (oldRecord != null)
            {
                oldData = DatasetAuditProvider.decodeFromDataMap(oldRecord);
            }
            if (oldData != null || newData != null)
            {
                if (oldData != null && newData != null)
                {
                    String oldLsid = oldData.get("lsid");
                    String newLsid = newData.get("lsid");
                    if (null != oldLsid && null != newLsid && !Strings.CI.equals(oldLsid, newLsid) && null != eventContainer)
                    {
                        ActionURL history = new ActionURL("audit", "begin", eventContainer);
                        history.addParameter("view","DatasetAuditEvent");
                        history.addParameter("query.lsid~eq", oldLsid);

                        view.addView(HtmlView.unsafe("Key values were modified.  <a href=\"" + PageFlowUtil.filter(history) + "\">[previous history]</a>"));
                    }
                }
                view.addView(new AuditChangesView(comment, oldData, newData));
            }
            else
            {
                view.addView(new NoRecordView());
            }

            return view;
        }

        private static class NoRecordView extends HttpView<Object>
        {
            @Override
            protected void renderInternal(Object model, PrintWriter out)
            {
                out.write("<p>No additional details recorded</p>");
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            Study study = getStudyThrowIfNull();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            root.addChild("Audit Log", new ActionURL("audit","begin", getContainer()).addParameter(DataRegion.LAST_FILTER_PARAM,1));
            root.addChild("Dataset Entry Detail");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class BulkDatasetDeleteAction extends FormViewAction<DatasetDeleteForm>
    {
        @Override
        public ModelAndView getView(DatasetDeleteForm form, boolean reshow, BindException errors)
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "/org/labkey/study/view/bulkDatasetDelete.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            _addNavTrailDatasetAdmin(root);
            root.addChild("Delete Datasets");
        }

        @Override
        public void validateCommand(DatasetDeleteForm target, Errors errors) {}

        @Override
        public boolean handlePost(DatasetDeleteForm form, BindException errors)
        {
            int[] datasetIds = form.getDatasetIds();
            int countDeleted = 0;

            if (datasetIds == null)
                return false;

            StudyImpl study = getStudyThrowIfNull();

            // Loop over each dataset, transacting per dataset to keep from locking out other users
            for (int datasetId : datasetIds)
            {
                DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, datasetId);
                if (def == null)
                    continue; // It's already been deleted; ignore it. User likely double-clicked.

                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    if (!def.canDeleteDefinition(getUser()))
                        continue;
                    StudyManager.getInstance().deleteDataset(study, getUser(), def, false, null);
                    transaction.commit();
                    countDeleted++;
                }
            }

            if (countDeleted > 0)
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(getUser(), Collections.emptySet());

            return true;
        }

        @Override
        public ActionURL getSuccessURL(DatasetDeleteForm datasetDeleteForm)
        {
            return new ActionURL(StudyController.ManageTypesAction.class, getContainer());
        }

    }

    public static class DatasetIdForm
    {
        private int datasetId;

        public int getDatasetId()
        {
            return datasetId;
        }

        public void setDatasetId(int datasetId)
        {
            this.datasetId = datasetId;
        }
    }

    public static class DatasetDeleteForm
    {
        private int[] datasetIds;

        public int[] getDatasetIds()
        {
            return datasetIds;
        }

        public void setDatasetIds(int[] datasetIds)
        {
            this.datasetIds = datasetIds;
        }
    }

    public static class DatasetAuditHistoryForm
    {
        private int auditRowId;

        public int getAuditRowId() {return auditRowId;}

        public void setAuditRowId(int auditRowId) {this.auditRowId = auditRowId;}
    }

    public static class DatasetAuditHistoryScopingTestCase extends AbstractContainerScopingTest
    {
        private static final String FIELD_VALUE = GUID.makeGUID();
        private Container _requestContainer;
        private long _foreignAuditRowId;

        @Before
        public void setup()
        {
            _requestContainer = createContainer("Request");
            StudyImpl study = new StudyImpl(_requestContainer, "Request Study");
            study.setTimepointType(TimepointType.VISIT);
            StudyManager.getInstance().createTestStudy(getAdmin(), study);

            _foreignAuditRowId = addDatasetAuditEvent(createContainer("Event"));
        }

        @Test
        public void doesNotDiscloseForeignFolderDatasetAuditEvent() throws Exception
        {
            // Even the site auditor, who may see audit logs everywhere, must not be served another folder's dataset
            // audit record when requesting it through this folder's URL: the lookup is scoped to the request container.
            String content = requestAuditHistory(_foreignAuditRowId, getAdmin()).getContentAsString();

            assertFalse("A foreign auditRowId must not disclose dataset audit record in another folder", content.contains(FIELD_VALUE));
            assertTrue("Should fall through to the 'no additional details' view", content.contains("No additional details recorded"));
        }

        @Test
        public void disclosesOwnFolderDatasetAuditEvent() throws Exception
        {
            // Control: an event in the request folder must be shown, proving the negative case isn't passing simply
            // because the view never renders record data.
            long localRowId = addDatasetAuditEvent(_requestContainer);
            String content = requestAuditHistory(localRowId, getAdmin()).getContentAsString();
            assertTrue("An audit event in the request folder must be shown", content.contains(FIELD_VALUE));
        }

        private long addDatasetAuditEvent(Container c)
        {
            DatasetAuditProvider.DatasetAuditEvent event = new DatasetAuditProvider.DatasetAuditEvent(c, "test dataset audit", 1);
            event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(Map.of("SecretField", FIELD_VALUE)));
            event = AuditLogService.get().addEvent(getAdmin(), event);
            return event.getRowId();
        }

        private MockHttpServletResponse requestAuditHistory(long auditRowId, User user) throws Exception
        {
            ActionURL url = new ActionURL(DatasetAuditHistoryAction.class, _requestContainer)
                    .addParameter("auditRowId", auditRowId);
            return get(url, user);
        }
    }
}
