/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import gwt.client.org.labkey.study.dataset.client.DatasetImporter;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.gwt.server.BaseRemoteService;
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
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.study.StudySchema;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.view.StudyGWTView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: jgarms
 */
public class DatasetController extends BaseStudyController
{
    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(DatasetController.class);

    public DatasetController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermission(ReadPermission.class)
    public class UpdateAction extends InsertUpdateAction
    {
        public UpdateAction()
        {
            super(EditDatasetRowForm.class);
        }

        protected boolean isInsert()
        {
            return false;
        }

        protected NavTree appendExtraNavTrail(NavTree root)
        {
            return root.addChild("Update Dataset Entry");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class InsertAction extends InsertUpdateAction
    {
        public InsertAction()
        {
            super(EditDatasetRowForm.class);
        }

        protected boolean isInsert()
        {
            return true;
        }

        protected NavTree appendExtraNavTrail(NavTree root)
        {
            return root.addChild("Insert " + _ds.getLabel());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DatasetAuditHistoryAction extends SimpleViewAction<DatasetAuditHistoryForm>
    {
        public ModelAndView getView(DatasetAuditHistoryForm form, BindException errors) throws Exception
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
                        objView.getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

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
            protected void renderInternal(Object model, PrintWriter out) throws Exception
            {
                out.write("<p>No current record found</p>");
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            Study study = getStudyThrowIfNull();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
            root.addChild("Audit Log", new ActionURL("audit","begin", getContainer()).addParameter(DataRegion.LAST_FILTER_PARAM,1));
            root.addChild("Dataset Entry Detail");
            return root;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class BulkDatasetDeleteAction extends FormViewAction<DatasetDeleteForm>
    {
        public ModelAndView getView(DatasetDeleteForm form, boolean reshow, BindException errors) throws Exception
        {
            return new StudyJspView<>(getStudyRedirectIfNull(), "bulkDatasetDelete.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendNavTrailDatasetAdmin(root);
            return root.addChild("Delete Datasets");
        }

        public void validateCommand(DatasetDeleteForm target, Errors errors) {}

        public boolean handlePost(DatasetDeleteForm form, BindException errors) throws Exception
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

    @RequiresPermission(AdminPermission.class)
    public class DefineAndImportDatasetAction extends SimpleViewAction<DatasetIdForm>
    {
        public ModelAndView getView(DatasetIdForm form, BindException errors) throws Exception
        {
            Map<String,String> props = new HashMap<>();

            Study study = getStudyRedirectIfNull();
            Dataset def = study.getDataset(form.getDatasetId());
            if (null == def)
                throw new NotFoundException("Invalid dataset id");

            props.put("typeURI", def.getTypeURI());

            props.put("timepointType", study.getTimepointType().toString());

            props.put("subjectColumnName", study.getSubjectColumnName());

            // Cancel should delete the dataset
            String cancelUrl = getViewContext().getActionURL().getParameter(ActionURL.Param.cancelUrl.name());
            if (cancelUrl == null)
            {
                ActionURL url = new ActionURL(
                    StudyController.DeleteDatasetAction.class, getContainer()).addParameter("id", form.getDatasetId());
                cancelUrl = url.getLocalURIString();
            }
            props.put(ActionURL.Param.cancelUrl.name(), cancelUrl);

            ActionURL successURL = new ActionURL(
                StudyController.DatasetAction.class, getContainer()).addParameter("datasetId", form.getDatasetId());
            props.put(ActionURL.Param.returnUrl.name(), successURL.getLocalURIString());

            // need a comma-separated list of base columns
            Set<String> baseColumnNames = def.getDefaultFieldNames();
            StringBuilder sb = new StringBuilder(DatasetTableImpl.QCSTATE_LABEL_COLNAME);
            for (String baseColumnName : baseColumnNames)
            {
                sb.append(",");
                sb.append(baseColumnName);
            }
            props.put("baseColumnNames", sb.toString());

            return new StudyGWTView(DatasetImporter.class, props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            _appendManageStudy(root);
            root.addChild("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, getContainer()));
            root.addChild("Define Dataset from File");
            return root;
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

    @RequiresPermission(AdminPermission.class)
    public class DomainImportServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new DatasetImportServiceImpl(getViewContext());
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
