/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

package org.labkey.cbcassay;

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.*;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.actions.ProtocolIdForm;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class CBCAssayController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver =
            new DefaultActionResolver(CBCAssayController.class);

    public CBCAssayController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(PageFlowUtil.urlProvider(ProjectUrls.class).getHomeURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static class EditResultsForm extends ProtocolIdForm
    {
        int _runId;
        int[] _objectId;
        String[] _sampleId;
        String _returnURL;

        public int getRunId()
        {
            return _runId;
        }

        public void setRunId(int runId)
        {
            _runId = runId;
        }

        public int[] getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(int[] objectId)
        {
            _objectId = objectId;
        }

        public String[] getSampleId()
        {
            return _sampleId;
        }

        public void setSampleId(String[] sampleId)
        {
            _sampleId = sampleId;
        }

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class EditResultsAction extends FormViewAction<EditResultsForm>
    {
        ExpProtocol _protocol;

        public void validateCommand(EditResultsForm form, Errors errors)
        {
        }

        public ModelAndView getView(EditResultsForm form, boolean reshow, BindException errors) throws Exception
        {
            _protocol = form.getProtocol(true);
            int runId = form.getRunId();

            AssaySchema schema = AssayService.get().createSchema(form.getUser(), form.getContainer());

            String name = AssayService.get().getResultsTableName(_protocol);
            QuerySettings settings = schema.getSettings(form.getViewContext(), name); //provider.getResultsQuerySettings();
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);
            settings.setMaxRows(0);
            settings.setShowRows(ShowRows.ALL);

            EditResultsQueryView queryView = new EditResultsQueryView(_protocol, schema, settings, runId, form.getReturnURL());
            queryView.setShowDetailsColumn(false);
            queryView.setShowExportButtons(false);
            queryView.setShowPagination(false);
            queryView.setShowRecordSelectors(true);

            return queryView;
        }

        public boolean handlePost(EditResultsForm form, BindException errors) throws Exception
        {
            CBCAssayProvider provider = (CBCAssayProvider) form.getProvider();
            ExpProtocol protocol = form.getProtocol();
            Domain resultDomain = provider.getResultsDomain(protocol);

            FieldKey sampleIdKey = provider.getTableMetadata().getParticipantIDFieldKey();
            PropertyDescriptor sampleIdPd = null;
            for (DomainProperty prop : resultDomain.getProperties())
            {
                if (prop.getName().equals(sampleIdKey.getName()))
                {
                    sampleIdPd = prop.getPropertyDescriptor();
                    break;
                }
            }

            assert sampleIdPd != null;

            boolean transaction = false;
            try
            {
                if (!ExperimentService.get().isTransactionActive())
                {
                    ExperimentService.get().beginTransaction();
                    transaction = true;
                }

                int[] objectIds = form.getObjectId();
                String[] sampleIds = form.getSampleId();
                assert objectIds.length == sampleIds.length;
                for (int i = 0; i < objectIds.length; i++)
                {
                    int objectId = objectIds[i];
                    String newSampleId = sampleIds[i];
                    OntologyObject obj = OntologyManager.getOntologyObject(objectId);
                    String objectURI = obj.getObjectURI();

                    // fetch current value of SampleId
                    Map<String, Object> oldValues = OntologyManager.getProperties(getContainer(), objectURI);
                    String oldSampleId = (String)oldValues.get(sampleIdPd.getPropertyURI());
                    if (oldSampleId == null && newSampleId == null)
                        continue;
                    if (oldSampleId != null && oldSampleId.equals(newSampleId))
                        continue;

                    OntologyManager.deleteProperty(objectURI, sampleIdPd.getPropertyURI(), getContainer(), getContainer());

                    if (newSampleId != null)
                    {
                        ObjectProperty oprop = new ObjectProperty(objectURI, getContainer(), sampleIdPd.getPropertyURI(), newSampleId);
                        OntologyManager.insertProperties(getContainer(), objectURI, oprop);
                    }
                }

                if (transaction)
                {
                    ExperimentService.get().commitTransaction();
                    transaction = false;
                }
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "An exception occurred: " + e);
                return false;
            }
            finally
            {
                if (transaction)
                    ExperimentService.get().rollbackTransaction();
            }

            return true;
        }

        public ActionURL getSuccessURL(EditResultsForm form)
        {
            String returnURL = form.getReturnURL();
            if (returnURL != null)
            {
                ActionURL url = new ActionURL(returnURL);
                return url.deleteParameter("returnURL");
            }
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(getContainer(), form.getProtocol());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Assay List", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer()));
            root.addChild(_protocol.getName() + " Batches", PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(getContainer(), _protocol, null));
            root.addChild(_protocol.getName() + " Runs", PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
            root.addChild(_protocol.getName() + " Results");
            return root;
        }
    }

    public static class DetailsForm extends ViewForm
    {
        private int _objectId;
        private String _returnURL;

        public int getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(int objectId)
        {
            _objectId = objectId;
        }

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
        }

        public void setSrcURL(String srcURL)
        {
            _returnURL = srcURL;
        }

        public ActionURL getReturnActionURL()
        {
            if (_returnURL == null)
                return null;
            
            try
            {
                return new ActionURL(_returnURL);
            }
            catch (Exception e)
            {
                throw new URLException(_returnURL, "returnURL parameter", e);
            }
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateAction extends FormViewAction<DetailsForm>
    {
        private CBCAssayProvider _provider;
        private ExpData _data;
        private ExpRun _run;
        private ExpProtocol _protocol;
        private ActionURL _returnURL;

        public void validateCommand(DetailsForm form, Errors errors)
        {
            if (_data == null)
            {
                _data = getProvider().getDataForDataRow(form.getObjectId());
                if (_data == null)
                    HttpView.throwNotFound("Data '" + form.getObjectId() + "' not found");
                _run = _data.getRun();
                if (_run == null)
                    HttpView.throwNotFound("Run not found");
                _protocol = _run.getProtocol();
                _returnURL = form.getReturnActionURL();
            }
        }

        CBCAssayProvider getProvider()
        {
            if (_provider == null)
                _provider = CBCAssayManager.get().getProvider();
            return _provider;
        }

        QueryView getResultsView()
        {
            // XXX: need to debug why I can't use the provider's queryView in the UpdateView
//            QueryView queryView = provider.createRunDataView(getViewContext(), _protocol);
            QuerySettings settings = getProvider().getResultsQuerySettings(getViewContext(), _protocol);
            QueryView queryView = new ResultsQueryView(_protocol, getViewContext(), settings);
            return queryView;
        }

        // XXX: move ListControler.setDisplayColumnsFromDefaultView to query
        // XXX: or use table.getUserModifiableColumns() ?
        private List<DisplayColumn> getUpdateableColumns(QueryView queryView)
        {
            List<DisplayColumn> displayColumns = queryView.getDisplayColumns();
            ListIterator<DisplayColumn> iter = displayColumns.listIterator();
            while (iter.hasNext())
            {
                DisplayColumn column = iter.next();
                if (column.getCaption().equals("Target Study"))
                    iter.remove();
                if (!column.isEditable() || column.getColumnInfo() instanceof LookupColumn)
                    iter.remove();
            }
            return displayColumns;
        }

        public ModelAndView getView(DetailsForm form, boolean reshow, BindException errors) throws Exception
        {
            validateCommand(form, errors);

            QueryView queryView = getResultsView();
            QueryUpdateForm quf = new QueryUpdateForm(queryView.getTable(), getViewContext(), errors);

            ButtonBar bb = new ButtonBar();
            ActionButton btnSubmit = new ActionButton(getViewContext().getActionURL(), "Submit");
//            ActionButton btnCancel = new ActionButton("Cancel", _returnURL);
            bb.add(btnSubmit);
//            bb.add(btnCancel);
            UpdateView view = new UpdateView(quf, errors);
            view.getDataRegion().setButtonBar(bb);

            view.getDataRegion().setDisplayColumns(getUpdateableColumns(queryView));

            return view;
        }

        public boolean handlePost(DetailsForm form, BindException errors) throws Exception
        {
            Domain resultsDomain = getProvider().getResultsDomain(_protocol);
            Map<String, DomainProperty> propMap = resultsDomain.createImportMap(false);

            QueryView queryView = getResultsView();
            QueryUpdateForm quf = new QueryUpdateForm(queryView.getTable(), getViewContext(), errors);
//            quf.populateValues(errors); // doesn't work, doesn't follow lookups

            if (errors.hasErrors())
                return false;

            boolean transaction = false;
            try
            {
                if (!ExperimentService.get().isTransactionActive())
                {
                    ExperimentService.get().beginTransaction();
                    transaction = true;
                }

                OntologyObject obj = OntologyManager.getOntologyObject(form.getObjectId());
                String objectURI = obj.getObjectURI();

                List<FieldKey> visibleColumns = quf.getTable().getDefaultVisibleColumns();
                Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(quf.getTable(), visibleColumns);

                // map column -> posted form value
                Map<FieldKey, Object> formValues = new HashMap<FieldKey, Object>();
                for (FieldKey key : visibleColumns)
                {
                    String field = key.toString();
                    String strValue = (String)quf.get(QueryUpdateForm.PREFIX + field);
                    if (strValue == null)
                        continue;

                    // XXX: I need to do my own conversion since QueryUpdateForm only converts
                    // values from the table.getColumns() set and not the set of visible columns.
                    ColumnInfo col = columns.get(key);
                    if (col == null)
                        throw new RuntimeException("expected ColumnInfo for field: " + field);

                    Object formValue = ConvertUtils.convert(strValue, col.getJavaClass());
                    formValues.put(key, formValue);
                }

                Map<String, Object> oldValues = (Map<String, Object>)quf.getOldValues();
                for (FieldKey key : formValues.keySet())
                {
                    ColumnInfo col = columns.get(key);
                    Object oldValue = oldValues.get(col.getSelectName());
                    Object newValue = formValues.get(key);

                    // don't allow deleting of values
                    if (newValue == null)
                        continue;

                    if (newValue.equals(oldValue))
                        continue;

                    // skip Date/Time for now
                    if (key.getName().toLowerCase().endsWith("date"))
                        continue;

                    DomainProperty dp = propMap.get(key.getName());
                    if (dp == null)
                        throw new RuntimeException("expected Property for " + key.getName());

                    PropertyDescriptor pd = dp.getPropertyDescriptor();
                    if (pd == null)
                        throw new RuntimeException("expected Property for " + key.getName());

                    if (oldValue != null)
                    {
                        OntologyManager.deleteProperty(objectURI, pd.getPropertyURI(), getContainer(), getContainer());
                    }

                    if (newValue != null)
                    {
                        ObjectProperty oprop = new ObjectProperty(objectURI, getContainer(), pd.getPropertyURI(), newValue);
                        OntologyManager.insertProperties(getContainer(), objectURI, oprop);
                    }
                }

                if (transaction)
                {
                    ExperimentService.get().commitTransaction();
                    transaction = false;
                }
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "An exception occurred: " + e);
                return false;
            }
            finally
            {
                if (transaction)
                    ExperimentService.get().rollbackTransaction();
            }

            return true;
        }

        public ActionURL getSuccessURL(DetailsForm form)
        {
            return _returnURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
}
