/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
import org.labkey.api.action.*;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.PkFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.actions.ProtocolIdForm;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.URLException;
import org.labkey.api.view.UpdateView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
        ReturnURLString _returnURL;

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

            CBCAssayProvider provider = (CBCAssayProvider)AssayService.get().getProvider(_protocol);
            AssayProtocolSchema schema = provider.createProtocolSchema(form.getUser(), form.getContainer(), _protocol, null);

            String name = AssayProtocolSchema.DATA_TABLE_NAME;
            QuerySettings settings = schema.getSettings(form.getViewContext(), name, name);
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);
            settings.setMaxRows(Table.ALL_ROWS);
            settings.setShowRows(ShowRows.ALL);

            EditResultsQueryView queryView = new EditResultsQueryView(_protocol, schema, settings, runId, form.getReturnUrl());
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

            FieldKey sampleIdKey = provider.getTableMetadata(protocol).getParticipantIDFieldKey();
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

            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                int[] objectIds = form.getObjectId();
                String[] sampleIds = form.getSampleId();
                assert objectIds.length == sampleIds.length;
                for (int i = 0; i < objectIds.length; i++)
                {
                    int objectId = objectIds[i];
                    String newSampleId = sampleIds[i];

                    SQLFragment sql = new SQLFragment("UPDATE " + resultDomain.getDomainKind().getStorageSchemaName());
                    sql.append("." + resultDomain.getStorageTableName() + " SET " + sampleIdKey.getName() + " = ? WHERE RowId = ?");
                    sql.add(newSampleId);
                    sql.add(objectId);

                    new SqlExecutor(DbSchema.get(resultDomain.getDomainKind().getStorageSchemaName())).execute(sql);
                }

                transaction.commit();
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "An exception occurred: " + e);
                return false;
            }

            return true;
        }

        public ActionURL getSuccessURL(EditResultsForm form)
        {
            ActionURL returnURL = form.getReturnActionURL();
            if (null != returnURL)
                return returnURL.deleteParameter(ActionURL.Param.returnUrl);
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

    public static class DetailsForm extends ProtocolIdForm
    {
        private int _dataRowId;
        private String _returnURL;

        public int getDataRowId()
        {
            return _dataRowId;
        }

        public void setDataRowId(int dataRowId)
        {
            _dataRowId = dataRowId;
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
        private ExpProtocol _protocol;
        private ActionURL _returnURL;

        public void validateCommand(DetailsForm form, Errors errors)
        {
            _protocol = form.getProtocol();
            _returnURL = form.getReturnActionURL();
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
            //QueryView queryView = getProvider().createResultsQueryView(getViewContext(), _protocol);

            AssayProtocolSchema schema = getProvider().createProtocolSchema(getUser(), getContainer(), _protocol, null);
            QuerySettings settings = schema.getSettings(getViewContext(), AssayProtocolSchema.DATA_TABLE_NAME, AssayProtocolSchema.DATA_TABLE_NAME);
            return new ResultsQueryView(_protocol, getViewContext(), settings);
        }

        // XXX: move ListControler.setDisplayColumnsFromDefaultView to query
        // XXX: or use table.getUserModifiableColumns() ?
        // XXX: or change these columns return false for .getShowInUpdateView()
        private List<DisplayColumn> getUpdateableColumns(QueryView queryView)
        {
            // Issue 12280: Get columns from the table's default visible list rather than the default view's columns.
            List<DisplayColumn> displayColumns = new ArrayList<>();
            Map<FieldKey, ColumnInfo> columnMap = QueryService.get().getColumns(queryView.getTable(), queryView.getTable().getDefaultVisibleColumns());
            for (ColumnInfo col : columnMap.values())
            {
                displayColumns.add(col.getRenderer());
            }

            List<DisplayColumn> ret = new ArrayList<>(displayColumns.size());
            for (DisplayColumn column : displayColumns)
            {
                if (column.getColumnInfo() != null && !column.getColumnInfo().isShownInUpdateView())
                    continue;
                if (column.getCaption().contains(AbstractAssayProvider.TARGET_STUDY_PROPERTY_CAPTION))
                    continue;
                if (!column.isEditable() || column.getColumnInfo() instanceof LookupColumn)
                    continue;
                ret.add(column);
            }
            return ret;
        }

        public ModelAndView getView(DetailsForm form, boolean reshow, BindException errors) throws Exception
        {
            validateCommand(form, errors);

            QueryView queryView = getResultsView();
            QueryUpdateForm quf = new QueryUpdateForm(queryView.getTable(), getViewContext(), errors);
            // Need to set the pkval ObjectId from dataRowId
            quf.setPkVal(form.getDataRowId());

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);
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

            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                List<FieldKey> visibleColumns = new ArrayList<>(quf.getTable().getDefaultVisibleColumns());
                visibleColumns.add(FieldKey.fromParts(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME));
                Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(quf.getTable(), visibleColumns);

                // map column -> posted form value
                Map<FieldKey, Object> formValues = new HashMap<>();
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

                // Get the oldValues directly instead of using the quf.getOldValues().
                // r13966 changed DataRegion.OLD_VALUES_NAME to not include any FieldKey lookups.
                Map<String, Object>[] maps = new TableSelector(quf.getTable(), columns.values(), new PkFilter(quf.getTable(), quf.getPkVals()), null).getMapArray();
                if (maps == null || maps.length != 1)
                    throw new RuntimeException("Didn't find existing row for '" + form.getDataRowId() + "'");
                Map<String, Object> oldValues = maps[0];
                Map<String, Object> newValues = new CaseInsensitiveHashMap<>(oldValues);

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

                    newValues.put(key.getName(), newValue);
                }

                QueryUpdateService updateService = quf.getTable().getUpdateService();
                updateService.updateRows(getUser(), getContainer(), Collections.singletonList(newValues), Collections.singletonList(oldValues), null, null);

                transaction.commit();
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "An exception occurred: " + e);
                return false;
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
