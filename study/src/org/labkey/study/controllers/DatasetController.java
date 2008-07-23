/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.*;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.util.*;

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

    @RequiresPermission(ACL.PERM_UPDATE)
    public class UpdateAction extends InsertUpdateAction
    {

        protected boolean isInsert()
        {
            return false;
        }

        protected NavTree appendExtraNavTrail(NavTree root)
        {
            return root.addChild("Update Dataset Entry");
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class InsertAction extends InsertUpdateAction
    {

        protected boolean isInsert()
        {
            return true;
        }

        protected NavTree appendExtraNavTrail(NavTree root)
        {
            return root.addChild("Insert new Dataset Entry");
        }
    }

    public abstract class InsertUpdateAction extends FormViewAction<EditDatasetRowForm>
    {
        protected abstract boolean isInsert();
        protected abstract NavTree appendExtraNavTrail(NavTree root);

        public ModelAndView getView(EditDatasetRowForm form, boolean reshow, BindException errors) throws Exception
        {
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
            if (null == ds)
            {
                redirectTypeNotFound(form.getDatasetId());
                return null;
            }
            if (!ds.canRead(getUser()))
            {
                throw new UnauthorizedException("User does not have permission to view this dataset");
            }

            TableInfo datasetTable = ds.getTableInfo(getUser());
            QueryUpdateForm updateForm = new QueryUpdateForm(datasetTable, getViewContext().getRequest());

            UpdateView view = new UpdateView(updateForm, errors);
            DataRegion dataRegion = view.getDataRegion();
            dataRegion.addHiddenFormField("datasetId", Integer.toString(form.getDatasetId()));

            String referer = form.getReturnURL();
            if (referer == null)
                referer = HttpView.currentRequest().getHeader("Referer");

            ActionURL cancelURL;

            if (referer == null)
            {
                cancelURL = new ActionURL(StudyController.DatasetAction.class, getContainer());
                cancelURL.addParameter("datasetId", form.getDatasetId());
            }
            else
            {
                cancelURL = new ActionURL(referer);
                dataRegion.addHiddenFormField("returnURL", referer);
            }
            ButtonBar buttonBar = dataRegion.getButtonBar(DataRegion.MODE_UPDATE);
            buttonBar = new ButtonBar(buttonBar); // need to copy since the original is read-only
            ActionButton cancelButton = new ActionButton(cancelURL.getLocalURIString(), "Cancel", DataRegion.MODE_UPDATE, ActionButton.Action.GET);
            cancelButton.setURL(cancelURL);
            buttonBar.add(1, cancelButton);
            if (isInsert())
            {
                // Need to update the URL to be the insert action
                buttonBar.getList().remove(0);
                buttonBar.add(0, ActionButton.BUTTON_DO_INSERT);
            }
            dataRegion.setButtonBar(buttonBar);

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            try
            {
                Study study = getStudy();
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
            DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
            if (null == ds)
            {
                redirectTypeNotFound(form.getDatasetId());
                return false;
            }
            if (!ds.canRead(getUser()))
            {
                throw new UnauthorizedException("User does not have permission to view this dataset");
            }

            TableInfo datasetTable = ds.getTableInfo(getUser());
            QueryUpdateForm updateForm = new QueryUpdateForm(datasetTable, getViewContext().getRequest());
            updateForm.populateValues(errors);

            if (errors.hasErrors())
                return false;

            // The form we have stores our new values in a map with "quf_" as a prefix. Clean that up,
            // and only use those fields. This excludes read-only data.
            Map<String,Object> data = getDataMap(updateForm);
            List<String> importErrors = new ArrayList<String>();

            String newLsid;
            if (isInsert())
            {
                newLsid = StudyService.get().insertDatasetRow(getUser(), getContainer(), datasetId, data, importErrors);
            }
            else
            {
                newLsid = StudyService.get().updateDatasetRow(getUser(), getContainer(), datasetId, form.getLsid(), data, importErrors);
            }

            if (importErrors.size() > 0)
            {
                for (String error : importErrors)
                {
                    errors.reject("update", error);
                }
                return false;
            }
            // If this results in a change to participant ID or the visit itself,
            // we need to recompute the participant-visit map
            if (isInsert() || !newLsid.equals(form.getLsid()))
            {
                StudyManager.getInstance().recomputeStudyDataVisitDate(getStudy());
                StudyManager.getInstance().getVisitManager(getStudy()).updateParticipantVisits(getUser());
            }

            return true;
        }

        // query update forms have all user data stored with the prefix "quf_".
        // Clear that off and return only the user data
        private Map<String,Object> getDataMap(QueryUpdateForm form)
        {
            Map<String,Object> data = new HashMap<String,Object>();
            for (Map.Entry<String,Object> entry : form.getTypedValues().entrySet())
            {
                String key = entry.getKey();
                if (key.startsWith("quf_"))
                {
                    key = key.substring(4);
                    data.put(key, entry.getValue());
                }
            }
            return data;
        }

        public ActionURL getSuccessURL(EditDatasetRowForm form)
        {
            if (form.getReturnURL() != null)
                return new ActionURL(form.getReturnURL());
            
            ActionURL url = new ActionURL(StudyController.DatasetAction.class, getContainer());
            url.addParameter("datasetId", form.getDatasetId());
            return url;
        }

    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DatasetAuditHistoryAction extends SimpleViewAction<DatasetAuditHistoryForm>
    {
        public ModelAndView getView(DatasetAuditHistoryForm form, BindException errors) throws Exception
        {
            int auditRowId = form.getAuditRowId();
            AuditLogEvent event = AuditLogService.get().getEvent(auditRowId);
            if (event == null)
            {
                HttpView.throwNotFound("Could not find event " + auditRowId + " to display.");
                return null;
            }
            VBox view = new VBox();

            Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer().getId(), event.getLsid());
            String oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT, "oldRecordMap"));
            String newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT, "newRecordMap"));

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
                    int datasetId = event.getIntKey1();
                    DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
                    if (null != ds)
                    {
                        TableInfo datasetTable = ds.getTableInfo(getUser());

                        TableViewForm objForm = new TableViewForm(datasetTable);
                        objForm.set("lsid", lsid);
                        objForm.set("datasetId", datasetId);

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
                DiffDetailsView diffView = new DiffDetailsView(event, oldData, newData);
                view.addView(diffView);
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
            try
            {
                Study study = getStudy();
                root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getContainer()));
                root.addChild("Study Overview", new ActionURL(StudyController.OverviewAction.class, getContainer()));
                root.addChild("Dataset Entry History");
                return root;
            }
            catch (ServletException se) {throw UnexpectedException.wrap(se);}
        }
    }

    private class DiffDetailsView extends WebPartView
    {
        private final AuditLogEvent event;
        private final Map<String,String> oldData;
        private final Map<String,String> newData;

        public DiffDetailsView(AuditLogEvent event, Map<String,String> oldData, Map<String,String> newData)
        {
            this.event = event;
            if (oldData != null)
            {
                this.oldData = new CaseInsensitiveHashMap<String>(oldData);
            }
            else
            {
                this.oldData = Collections.emptyMap();
            }
            if (newData != null)
            {
                this.newData = new CaseInsensitiveHashMap<String>(newData);
            }
            else
            {
                this.newData = Collections.emptyMap();
            }
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            int modified = 0;

            out.write("<table>\n");
            out.write("<tr class=\"wpHeader\"><th colspan=\"2\" class=\"wpTitle\" align=\"left\">Item Changes</th></tr>");
            out.write("<tr><td colspan=\"2\">Comment:&nbsp;<i>" + PageFlowUtil.filter(event.getComment()) + "</i></td></tr>");
            out.write("<tr><td/>\n");

            for (Map.Entry<String, String> entry : oldData.entrySet())
            {
                out.write("<tr><td class=\"ms-searchform\">");
                out.write(entry.getKey());
                out.write("</td><td>");

                StringBuffer sb = new StringBuffer();
                String oldValue = entry.getValue();
                if (oldValue == null)
                    oldValue = "";
                sb.append(oldValue);

                String newValue = newData.remove(entry.getKey());
                if (newValue == null)
                    newValue = "";
                if (!newValue.equals(oldValue))
                {
                    modified++;
                    sb.append("&nbsp;&raquo;&nbsp;");
                    sb.append(newValue);
                }
                out.write(sb.toString());
                out.write("</td></tr>\n");
            }

            for (Map.Entry<String, String> entry : newData.entrySet())
            {
                modified++;
                out.write("<tr><td class=\"ms-searchform\">");
                out.write(entry.getKey());
                out.write("</td><td>");

                StringBuffer sb = new StringBuffer();
                sb.append("&nbsp;&raquo;&nbsp;");
                String newValue = entry.getValue();
                if (newValue == null)
                    newValue = "";
                sb.append(newValue);
                out.write(sb.toString());
                out.write("</td></tr>\n");
            }
            out.write("<tr><td/>\n");
            out.write("<tr><td colspan=\"2\">Summary:&nbsp;<i>");
            out.write(modified + " field(s) were modified</i></td></tr>");
            out.write("</table>\n");
        }
    }

    public static class EditDatasetRowForm
    {
        private String lsid;
        private int datasetId;
        private String returnURL;

        public String getLsid() {return lsid;}
        public void setLsid(String lsid) {this.lsid = lsid;}
        public int getDatasetId() {return datasetId;}
        public void setDatasetId(int datasetId) {this.datasetId = datasetId;}
        public String getReturnURL() {return returnURL;}
        public void setReturnURL(String returnURL) {this.returnURL = returnURL;}
    }

    public static class DatasetAuditHistoryForm
    {
        private int auditRowId;

        public int getAuditRowId() {return auditRowId;}

        public void setAuditRowId(int auditRowId) {this.auditRowId = auditRowId;}

    }

}
