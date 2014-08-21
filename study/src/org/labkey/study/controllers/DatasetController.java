/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.DataSet;
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
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetTableImpl;
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

    @RequiresPermissionClass(ReadPermission.class)
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

    @RequiresPermissionClass(ReadPermission.class)
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
            return root.addChild("Insert new entry: " + _ds.getLabel());
        }
    }

/*
    public abstract class InsertUpdateAction extends FormViewAction<EditDatasetRowForm>
    {
        protected abstract boolean isInsert();
        protected abstract NavTree appendExtraNavTrail(NavTree root);

        public ModelAndView getView(EditDatasetRowForm form, boolean reshow, BindException errors) throws Exception
        {
            StudyImpl study = getStudyRedirectIfNull();
            DataSetDefinition ds = StudyManager.getInstance().getDatasetDefinition(study, form.getDatasetId());
            if (null == ds)
            {
                redirectTypeNotFound(form.getDatasetId());
                return null;
            }
            if (!ds.canRead(getUser()))
            {
                throw new UnauthorizedException("User does not have permission to view this dataset");
            }

            TableInfo datasetTable = ds.getTableInfo(getUser(), true, false);

            // if this is our cohort assignment dataset, we may want to display drop-downs for cohort, rather
            // than a text entry box:
            if (!study.isManualCohortAssignment() && safeEquals(ds.getDatasetId(), study.getParticipantCohortDatasetId()))
            {
                final Cohort[] cohorts = StudyManager.getInstance().getCohorts(study.getContainer(), getUser());
                ColumnInfo cohortCol = datasetTable.getColumn(study.getParticipantCohortProperty());
                if (cohortCol != null && cohortCol.getSqlTypeInt() == Types.VARCHAR)
                {
                    cohortCol.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new DataColumn(colInfo)
                            {
                                @Override
                                public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
                                {
                                    boolean disabledInput = isDisabledInput();
                                    String formFieldName = ctx.getForm().getFormFieldName(getBoundColumn());
                                    out.write("<select name=\"" + formFieldName + "\" " + (disabledInput ? "DISABLED" : "") + ">\n");
                                    if (getBoundColumn().isNullable())
                                        out.write("\t<option value=\"\">");
                                    for (Cohort cohort : cohorts)
                                    {
                                        out.write("\t<option value=\"" + PageFlowUtil.filter(cohort.getLabel()) + "\" " +
                                                (safeEquals(value, cohort.getLabel()) ? "SELECTED" : "") + ">");
                                        out.write(PageFlowUtil.filter(cohort.getLabel()));
                                        out.write("</option>\n");
                                    }
                                    out.write("</select>");
                                }
                            };
                        }
                    });
                }
            }

            QueryUpdateForm updateForm = new QueryUpdateForm(datasetTable, getViewContext());

            DataView view;
            if (isInsert())
            {
                view = new InsertView(updateForm, errors);
                if (!reshow)
                {
                    Domain domain = PropertyService.get().getDomain(getContainer(), ds.getTypeURI());
                    if (domain != null)
                    {
                        Map<DomainProperty, Object> defaults = DefaultValueService.get().getDefaultValues(getContainer(), domain, getUser());
                        Map<String, String> formDefaults = new HashMap<String, String>();
                        for (Map.Entry<DomainProperty, Object> entry : defaults.entrySet())
                        {
                            if (entry.getValue() != null)
                            {
                                String stringValue = entry.getValue().toString();
                                ColumnInfo temp = entry.getKey().getPropertyDescriptor().createColumnInfo(datasetTable, "LSID", getUser());
                                formDefaults.put(updateForm.getFormFieldName(temp), stringValue);
                            }
                        }
                        ((InsertView) view).setInitialValues(formDefaults);
                    }
                }
            }
            else
                view = new UpdateView(updateForm, errors);
            
            DataRegion dataRegion = view.getDataRegion();

            String referer = form.getReturnURL();
            if (referer == null)
                referer = HttpView.currentRequest().getHeader("Referer");

            ActionURL cancelURL;

            if (referer == null)
            {
                cancelURL = new ActionURL(StudyController.DatasetAction.class, getContainer());
                cancelURL.addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
            }
            else
            {
                cancelURL = new ActionURL(referer);
                dataRegion.addHiddenFormField("returnURL", referer);
            }

            ButtonBar buttonBar = new ButtonBar();
            ActionButton btnSubmit = new ActionButton(new ActionURL(getClass(), getContainer()).addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId()), "Submit");
            ActionButton btnCancel = new ActionButton("Cancel", cancelURL);
            buttonBar.add(btnSubmit);
            buttonBar.add(btnCancel);

            dataRegion.setButtonBar(buttonBar);
            return new VBox(new HtmlView("<script type=\"text/javascript\">LABKEY.requiresScript(\"completion.js\");</script>"), view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                Study study = getStudyRedirectIfNull();
                root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
                root.addChild("Study Overview", new ActionURL(StudyController.OverviewAction.class, getContainer()));
                appendExtraNavTrail(root);
            }
            catch (ServletException e) {}
            return root;
        }

        public void validateCommand(EditDatasetRowForm target, Errors errors) {}

        public boolean handlePost(EditDatasetRowForm form, BindException errors) throws Exception
        {
            int datasetId = form.getDatasetId();
            DataSetDefinition ds = StudyManager.getInstance().getDatasetDefinition(getStudyRedirectIfNull(), datasetId);
            if (null == ds)
            {
                redirectTypeNotFound(form.getDatasetId());
                return false;
            }
            if (!ds.canWrite(getUser()))
            {
                throw new UnauthorizedException("User does not have permission to edit this dataset");
            }
            if (ds.getProtocolId() != null)
            {
                throw new UnauthorizedException("This dataset comes from an assay. You cannot update it directly");
            }

            TableInfo datasetTable = ds.getTableInfo(getUser());
            QueryUpdateForm updateForm = new QueryUpdateForm(datasetTable, getViewContext());
            //noinspection ThrowableResultOfMethodCallIgnored
            updateForm.populateValues(errors);

            if (errors.hasErrors())
                return false;

            Map<String,Object> data = updateForm.getDataMap();
            List<String> importErrors = new ArrayList<String>();

            String newLsid;
            if (isInsert())
            {
                newLsid = StudyService.get().insertDatasetRow(getUser(), getContainer(), datasetId, data, importErrors);

                // save last inputs for use in default value population:
                Domain domain = PropertyService.get().getDomain(getContainer(), ds.getTypeURI());
                DomainProperty[] properties = domain.getProperties();
                Map<String, Object> requestMap = updateForm.getTypedValues();
                Map<DomainProperty, Object> dataMap = new HashMap<DomainProperty, Object>(requestMap.size());
                for (DomainProperty property : properties)
                {
                    ColumnInfo currentColumn = property.getPropertyDescriptor().createColumnInfo(datasetTable, "LSID", getUser());
                    Object value = requestMap.get(updateForm.getFormFieldName(currentColumn));
                    if (property.isMvEnabled())
                    {
                        ColumnInfo mvColumn = datasetTable.getColumn(property.getName() + MvColumn.MV_INDICATOR_SUFFIX);
                        String mvIndicator = (String)requestMap.get(updateForm.getFormFieldName(mvColumn));
                        MvFieldWrapper mvWrapper = new MvFieldWrapper(value, mvIndicator);
                        dataMap.put(property, mvWrapper);
                    }
                    else
                    {
                        dataMap.put(property, value);
                    }
                }
                DefaultValueService.get().setDefaultValues(getContainer(), dataMap, getUser());
            }
            else
            {
                newLsid = StudyService.get().updateDatasetRow(getUser(), getContainer(), datasetId, form.getLsid(), data, importErrors);
            }

            if (importErrors.size() > 0)
            {
                for (String error : importErrors)
                {
                    errors.reject("update", PageFlowUtil.filter(error));
                }
                return false;
            }
            // If this results in a change to participant ID or the visit itself,
            // we need to recompute the participant-visit map
            if (isInsert() || !newLsid.equals(form.getLsid()))
            {
                StudyManager.getInstance().recomputeStudyDataVisitDate(getStudyRedirectIfNull());
                StudyManager.getInstance().getVisitManager(getStudyRedirectIfNull()).updateParticipantVisits(getUser());
            }

            if (safeEquals(form.getDatasetId(), getStudyRedirectIfNull().getParticipantCohortDatasetId()))
                CohortManager.getInstance().updateParticipantCohorts(getUser(), getStudyRedirectIfNull());

            return true;
        }

        public ActionURL getSuccessURL(EditDatasetRowForm form)
        {
            if (form.getReturnURL() != null)
                return new ActionURL(form.getReturnURL());
            
            ActionURL url = new ActionURL(StudyController.DatasetAction.class, getContainer());
            url.addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
            if (StudyManager.getInstance().showQCStates(getContainer()))
            {
                QCStateSet stateSet = QCStateSet.getAllStates(getContainer());
                url.addParameter(BaseStudyController.SharedFormParameters.QCState, stateSet.getFormValue());
            }
            return url;
        }

    }

*/
    @RequiresPermissionClass(AdminPermission.class)
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

            if (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(DatasetAuditProvider.DATASET_AUDIT_EVENT))
            {
                DatasetAuditProvider.DatasetAuditEvent event = AuditLogService.get().getAuditEvent(getUser(), DatasetAuditProvider.DATASET_AUDIT_EVENT, auditRowId);

                if (event != null)
                {
                    comment = event.getComment();
                    oldRecord = event.getOldRecordMap();
                    newRecord = event.getNewRecordMap();
                    datasetId = event.getDatasetId();
                    eventContainer = ContainerManager.getForId(event.getContainer());
                }
            }
            else
            {
                AuditLogEvent event = AuditLogService.get().getEvent(auditRowId);
                if (event == null)
                {
                    throw new NotFoundException("Could not find event " + auditRowId + " to display.");
                }
                Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer(), event.getLsid());
                oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT,
                        DatasetAuditViewFactory.OLD_RECORD_PROP_NAME));
                newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT,
                        DatasetAuditViewFactory.NEW_RECORD_PROP_NAME));

                datasetId = null==event.getIntKey1() ? -1 : event.getIntKey1().intValue();
                eventContainer = ContainerManager.getForId(event.getContainerId());
            }


            Map<String,String> oldData = null;
            Map<String,String> newData = null;
            // If the record was deleted, newRecord will be null. Otherwise we might be able to find it
            if (newRecord != null)
            {
                newData = DatasetAuditViewFactory.decodeFromDataMap(newRecord);
                String lsid = newData.get("lsid");
                if (lsid != null)
                {
                    // If we have a current record, display it
                    DataSet ds = StudyManager.getInstance().getDatasetDefinition(getStudyRedirectIfNull(), datasetId);
                    if (null != ds)
                    {
                        TableInfo datasetTable = ds.getTableInfo(getUser());

                        TableViewForm objForm = new TableViewForm(datasetTable);
                        objForm.set("lsid", lsid);
                        objForm.set(DataSetDefinition.DATASETKEY, datasetId);

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
                oldData = DatasetAuditViewFactory.decodeFromDataMap(oldRecord);
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

                        if (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(DatasetAuditProvider.DATASET_AUDIT_EVENT))
                            history.addParameter("query.lsid~eq", oldLsid);
                        else
                            history.addParameter("audit.Key1~eq", oldLsid);

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

    @RequiresPermissionClass(AdminPermission.class)
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
                DataSetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, datasetId);
                if (def == null)
                    continue; // It's already been deleted; ignore it. User likely double-clicked.

                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    StudyManager.getInstance().deleteDataset(study, getUser(), def, false);
                    transaction.commit();
                    countDeleted++;
                }
            }

            if (countDeleted > 0)
                StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(getUser(), Collections.<DataSetDefinition>emptySet());

            return true;
        }

        public ActionURL getSuccessURL(DatasetDeleteForm datasetDeleteForm)
        {
            return new ActionURL(StudyController.ManageTypesAction.class, getContainer());
        }

    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DefineAndImportDatasetAction extends SimpleViewAction<DatasetIdForm>
    {
        public ModelAndView getView(DatasetIdForm form, BindException errors) throws Exception
        {
            Map<String,String> props = new HashMap<>();

            Study study = getStudyRedirectIfNull();
            DataSet def = study.getDataset(form.getDatasetId());
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
            StringBuilder sb = new StringBuilder(DataSetTableImpl.QCSTATE_LABEL_COLNAME);
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

    @RequiresPermissionClass(AdminPermission.class)
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
