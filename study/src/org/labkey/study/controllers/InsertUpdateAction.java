/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.study.StudyModule;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * User: klum
 * Date: Sep 24, 2009
 */
public abstract class InsertUpdateAction<Form extends DatasetController.EditDatasetRowForm> extends FormViewAction<Form>
{
    protected abstract boolean isInsert();
    protected abstract NavTree appendExtraNavTrail(NavTree root);
    protected StudyImpl _study = null;
    private QueryUpdateForm _updateForm;
    protected DatasetDefinition _ds = null;

    protected InsertUpdateAction(Class<? extends Form> formClass)
    {
        super(formClass);
    }

    private QueryUpdateForm getUpdateForm(TableInfo datasetTable, BindException errors)
    {
        if (_updateForm == null)
        {
            QueryUpdateForm ret = new QueryUpdateForm(datasetTable, getViewContext(), errors);

            if (errors.hasErrors())
                return ret;

            _updateForm = ret;
        }
        return _updateForm;
    }

    public ModelAndView getView(Form form, boolean reshow, BindException errors) throws Exception
    {
        StudyImpl study = getStudy();
        _ds = StudyManager.getInstance().getDatasetDefinition(getStudy(), form.getDatasetId());
        if (null == _ds)
        {
            redirectTypeNotFound(form.getDatasetId());
            return null;
        }
        if (!_ds.canRead(getUser()))
        {
            throw new UnauthorizedException("User does not have permission to view this dataset");
        }
        if (!_ds.canWrite(getUser()))
        {
            throw new UnauthorizedException("User does not have permission to edit this dataset");
        }

        // we want to use the actual user schema table, since it implements UpdateService and permissions checks
        TableInfo datasetTable = getQueryTable();

        // if this is our cohort assignment dataset, we may want to display drop-downs for cohort, rather
        // than a text entry box:
        if (!study.isManualCohortAssignment() && Objects.equals(_ds.getDatasetId(), study.getParticipantCohortDatasetId()))
        {
            final List<? extends Cohort> cohorts = StudyManager.getInstance().getCohorts(study.getContainer(), getUser());
            String participantCohortPropertyName = study.getParticipantCohortProperty();
            if (participantCohortPropertyName != null)
            {
                ColumnInfo cohortCol = datasetTable.getColumn(participantCohortPropertyName);
                if (cohortCol != null && cohortCol.getSqlTypeInt() == Types.VARCHAR)
                {
                    cohortCol.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo)
                    {
                        @Override
                        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
                        {
                            boolean disabledInput = isDisabledInput();
                            String formFieldName = ctx.getForm().getFormFieldName(getBoundColumn());

                            out.write("<select name=\"" + formFieldName + "\" " + (disabledInput ? "DISABLED" : ""));
                            out.write(" class=\"form-control\">\n");
                            if (getBoundColumn().isNullable())
                                out.write("\t<option value=\"\">");
                            for (Cohort cohort : cohorts)
                            {
                                out.write("\t<option value=\"" + PageFlowUtil.filter(cohort.getLabel()) + "\" " +
                                        (Objects.equals(value, cohort.getLabel()) ? "SELECTED" : "") + ">");
                                out.write(PageFlowUtil.filter(cohort.getLabel()));
                                out.write("</option>\n");
                            }
                            out.write("</select>");
                        }
                    });
                }
            }
        }

        QueryUpdateForm updateForm = getUpdateForm(datasetTable, errors);

        DataView view = createNewView(form, updateForm, errors);
        if (isInsert())
        {
            if (!reshow)
            {
                Domain domain = PropertyService.get().getDomain(getContainer(), _ds.getTypeURI());
                if (domain != null)
                {
                    Map<DomainProperty, Object> defaults = DefaultValueService.get().getDefaultValues(getContainer(), domain, getUser());
                    Map<String, Object> formDefaults = new HashMap<>();
                    for (Map.Entry<DomainProperty, Object> entry : defaults.entrySet())
                    {
                        if (entry.getValue() != null)
                        {
                            String stringValue = entry.getValue().toString();
                            ColumnInfo temp = entry.getKey().getPropertyDescriptor().createColumnInfo(datasetTable, "LSID", getUser(), getContainer());
                            formDefaults.put(updateForm.getFormFieldName(temp), stringValue);
                        }
                    }
                    ((InsertView) view).setInitialValues(formDefaults);
                }
            }
        }
        DataRegion dataRegion = view.getDataRegion();

        String referer = StringUtils.defaultString(form.getReturnUrl(), HttpView.currentRequest().getHeader("Referer"));
        URLHelper cancelURL;

        if (StringUtils.isEmpty(referer))
        {
            cancelURL = new ActionURL(StudyController.DatasetAction.class, getContainer());
            cancelURL.addParameter(DatasetDefinition.DATASETKEY, ""+form.getDatasetId());
        }
        else
        {
            cancelURL = new URLHelper(referer);
            dataRegion.addHiddenFormField(ActionURL.Param.returnUrl, referer);
        }

        ButtonBar buttonBar = new ButtonBar();
        buttonBar.setStyle(ButtonBar.Style.separateButtons);
        ActionButton btnSubmit = new ActionButton(new ActionURL(getClass(), getContainer()).addParameter(DatasetDefinition.DATASETKEY, form.getDatasetId()), "Submit");
        ActionButton btnCancel = new ActionButton("Cancel", cancelURL);
        buttonBar.add(btnSubmit);
        buttonBar.add(btnCancel);
        buttonBar.setStyle(ButtonBar.Style.separateButtons);
        dataRegion.setButtonBar(buttonBar);

        view.addClientDependency(ClientDependency.fromPath("completion"));

        return view;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        try
        {
            Study study = getStudy();
            Container container = getContainer();
            ActionURL rootURL;
            if (container.getFolderType().getDefaultModule() instanceof StudyModule)
            {
                rootURL = container.getFolderType().getStartURL(container, getUser());
            }
            else
            {
                rootURL = new ActionURL(StudyController.BeginAction.class, container);
            }
            root.addChild(study.getLabel(), rootURL);
            ActionURL grid = new ActionURL(StudyController.DatasetAction.class, getContainer());
            grid.addParameter(DatasetDefinition.DATASETKEY, _ds.getDatasetId());
            grid.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
            root.addChild(_ds.getLabel(), grid);
            appendExtraNavTrail(root);
        }
        catch (ServletException e) {}
        return root;
    }

    protected DataView createNewView(Form form, QueryUpdateForm updateForm, BindException errors)
    {
        if (isInsert())
            return new InsertView(updateForm, errors);
        else
            return new UpdateView(updateForm, errors);
    }

    public void validateCommand(Form target, Errors errors) {}

    public boolean handlePost(Form form, BindException errors) throws Exception
    {
        int datasetId = form.getDatasetId();
        StudyImpl study = getStudy();
        _ds = StudyManager.getInstance().getDatasetDefinition(study, datasetId);
        if (null == _ds)
        {
            redirectTypeNotFound(form.getDatasetId());
            return false;
        }
        final Container c = getContainer();
        final User user = getUser();
        if (!_ds.canWrite(user))
        {
            throw new UnauthorizedException("User does not have permission to edit this dataset");
        }
        if (_ds.isAssayData())
        {
            throw new UnauthorizedException("This dataset comes from an assay. You cannot update it directly");
        }

        TableInfo datasetTable = getQueryTable();
        QueryUpdateForm updateForm = getUpdateForm(datasetTable, errors);

        if (errors.hasErrors())
            return false;

        // The query Dataset table supports QueryUpdateService while the table returned from ds.getTableInfo() doesn't.
        QueryUpdateService qus = datasetTable.getUpdateService();
        assert qus != null;

        DbSchema dbschema = datasetTable.getSchema();
        try (DbScope.Transaction transaction = dbschema.getScope().ensureTransaction())
        {
            Map<String,Object> data = updateForm.getTypedColumns();

            if (isInsert())
            {
                BatchValidationException batchErrors = new BatchValidationException();
                List<Map<String, Object>> insertedRows = qus.insertRows(user, c, Collections.singletonList(data), batchErrors, null, null);
                if (batchErrors.hasErrors())
                    throw batchErrors;
                if (insertedRows.size() == 0)
                    return false;

                // save last inputs for use in default value population:
                Domain domain = PropertyService.get().getDomain(c, _ds.getTypeURI());
                List<? extends DomainProperty> properties = domain.getProperties();
                Map<String, Object> requestMap = updateForm.getTypedValues();
                Map<DomainProperty, Object> dataMap = new HashMap<>(requestMap.size());
                for (DomainProperty property : properties)
                {
                    ColumnInfo currentColumn = property.getPropertyDescriptor().createColumnInfo(datasetTable, "LSID", user, getContainer());
                    Object value = requestMap.get(updateForm.getFormFieldName(currentColumn));
                    if (property.isMvEnabled())
                    {
                        ColumnInfo mvColumn = datasetTable.getColumn(property.getName() + MvColumn.MV_INDICATOR_SUFFIX);
                        String mvIndicator = (String)requestMap.get(updateForm.getFormFieldName(mvColumn));
                        MvFieldWrapper mvWrapper = new MvFieldWrapper(MvUtil.getMvIndicators(c), value, mvIndicator);
                        dataMap.put(property, mvWrapper);
                    }
                    else
                    {
                        dataMap.put(property, value);
                    }
                }
                DefaultValueService.get().setDefaultValues(c, dataMap, user);
            }
            else
            {
                List<Map<String, Object>> updatedRows = qus.updateRows(user, c, Collections.singletonList(data),
                        Collections.singletonList(Collections.singletonMap("lsid", form.getLsid())), null, null);
                if (updatedRows.size() == 0)
                    return false;
            }

            transaction.commit();
        }
        catch (SQLException x)
        {
            if (!RuntimeSQLException.isConstraintException(x))
                throw x;
            errors.reject(SpringActionController.ERROR_MSG, x.getMessage());
        }
        catch (BatchValidationException x)
        {
            errors.addAllErrors(errors);
            x.addToErrors(errors);
        }

        return !errors.hasErrors();

    }

    public ActionURL getSuccessURL(Form form)
    {
        ActionURL url = form.getReturnActionURL();
        if (null != url)
            return url;

        url = new ActionURL(StudyController.DatasetAction.class, getContainer());
        url.addParameter(DatasetDefinition.DATASETKEY, form.getDatasetId());
        if (StudyManager.getInstance().showQCStates(getContainer()))
        {
            QCStateSet stateSet = QCStateSet.getAllStates(getContainer());
            url.addParameter(BaseStudyController.SharedFormParameters.QCState, stateSet.getFormValue());
        }
        return url;
    }

    protected StudyImpl getStudy() throws ServletException
    {
        if (null == _study)
            _study = BaseStudyController.getStudyRedirectIfNull(getContainer());
        return _study;
    }

    protected void redirectTypeNotFound(int datasetId) throws RedirectException
    {
        throw new RedirectException(new ActionURL(StudyController.TypeNotFoundAction.class, getContainer()).addParameter("id", datasetId));
    }


    TableInfo getQueryTable() throws ServletException
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(getStudy(), getUser(), true);
        TableInfo datasetQueryTable= schema.getTable(_ds.getName());
        if (null == datasetQueryTable) // shouldn't happen...
            throw new NotFoundException("table: study." + _ds.getName());
        return datasetQueryTable;
    }
}
