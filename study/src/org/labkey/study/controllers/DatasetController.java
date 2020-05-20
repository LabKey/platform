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

package org.labkey.study.controllers;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.labkey.study.StudySchema;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
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
    public class UpdateAction extends InsertUpdateAction
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
    public class InsertAction extends InsertUpdateAction
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

    @RequiresPermission(AdminPermission.class)
    public class DatasetAuditHistoryAction extends SimpleViewAction<DatasetAuditHistoryForm>
    {
        public ModelAndView getView(DatasetAuditHistoryForm form, BindException errors)
        {
            int auditRowId = form.getAuditRowId();
            String comment = null;
            String oldRecord = null;
            String newRecord = null;
            int datasetId = -1;
            Container eventContainer = null;

            VBox view = new VBox();

            DatasetAuditProvider.DatasetAuditEvent event = AuditLogService.get().getAuditEvent(getUser(), DatasetAuditProvider.DATASET_AUDIT_EVENT, auditRowId);
            if (event != null)
            {
                comment = event.getComment();
                oldRecord = event.getOldRecordMap();
                newRecord = event.getNewRecordMap();
                datasetId = event.getDatasetId();
                eventContainer = ContainerManager.getForId(event.getContainer());
            }

            Map<String,String> oldData = null;
            Map<String,String> newData = null;
            // If the record was deleted, newRecord will be null. Otherwise we might be able to find it
            if (newRecord != null)
            {
                newData = DatasetAuditProvider.decodeFromDataMap(newRecord);
                String lsid = newData.get("lsid");
                if (lsid != null)
                {
                    // If we have a current record, display it
                    Dataset ds = StudyManager.getInstance().getDatasetDefinition(getStudyRedirectIfNull(), datasetId);
                    if (null != ds)
                    {
                        TableInfo datasetTable = ds.getTableInfo(getUser());

                        TableViewForm objForm = new TableViewForm(datasetTable);
                        objForm.set("lsid", lsid);
                        objForm.set(DatasetDefinition.DATASETKEY, datasetId);

                        DetailsView objView = new DetailsView(objForm);

                        view.addView(objView);
                    }
                }
                else
                {
                    view.addView(new NoRecordView());
                }
            }
            else
            {
                view.addView(new NoRecordView());
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
                    if (null != oldLsid && null != newLsid && !StringUtils.equalsIgnoreCase(oldLsid, newLsid) && null != eventContainer)
                    {
                        ActionURL history = new ActionURL("audit", "begin", eventContainer);
                        history.addParameter("view","DatasetAuditEvent");
                        history.addParameter("query.lsid~eq", oldLsid);

                        view.addView(new HtmlView(
                            "Key values were modified.  <a href=\"" + history + "\">[previous history]</a>"
                        ));
                    }
                }
                view.addView(new AuditChangesView(comment, oldData, newData));
            }

            return view;
        }

        private class NoRecordView extends HttpView
        {
            @Override
            protected void renderInternal(Object model, PrintWriter out)
            {
                out.write("<p>No current record found</p>");
            }
        }

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
        public ModelAndView getView(DatasetDeleteForm form, boolean reshow, BindException errors)
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "bulkDatasetDelete.jsp", form, errors);
        }

        public void addNavTrail(NavTree root)
        {
            _addNavTrailDatasetAdmin(root);
            root.addChild("Delete Datasets");
        }

        public void validateCommand(DatasetDeleteForm target, Errors errors) {}

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
                    StudyManager.getInstance().deleteDataset(study, getUser(), def, false);
                    transaction.commit();
                    countDeleted++;
                }
            }

            if (countDeleted > 0)
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(getUser(), Collections.emptySet());

            return true;
        }

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

    public static class EditDatasetRowForm extends ReturnUrlForm
    {
        private String lsid;
        private int datasetId;

        public String getLsid() {return lsid;}
        public void setLsid(String lsid) {this.lsid = lsid;}
        public int getDatasetId() {return datasetId;}
        public void setDatasetId(int datasetId) {this.datasetId = datasetId;}
    }

    public static class DatasetAuditHistoryForm
    {
        private int auditRowId;

        public int getAuditRowId() {return auditRowId;}

        public void setAuditRowId(int auditRowId) {this.auditRowId = auditRowId;}

    }
}
