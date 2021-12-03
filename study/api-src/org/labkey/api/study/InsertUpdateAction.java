/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.api.study;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
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
import org.labkey.api.module.Module;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.model.CohortService;
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
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * User: klum
 * Date: Sep 24, 2009
 */
public abstract class InsertUpdateAction<Form extends EditDatasetRowForm> extends FormViewAction<Form>
{
    private static final String DEFAULT_INSERT_VALUE_PREFIX = "default.";

    protected abstract boolean isInsert();
    protected abstract void addExtraNavTrail(NavTree root);
    protected Study _study = null;
    private QueryUpdateForm _updateForm;
    protected Dataset _ds = null;

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

    @Override
    public ModelAndView getView(Form form, boolean reshow, BindException errors) throws Exception
    {
        Study study = getStudy();

        _ds = StudyService.get().getDataset(study.getContainer(), form.getDatasetId());
        if (null == _ds)
        {
            redirectTypeNotFound(form.getDatasetId());
            return null;
        }

        TableInfo datasetTable = getQueryTable();
        if (!datasetTable.hasPermission(getUser(), ReadPermission.class))
        {
            throw new UnauthorizedException("User does not have permission to view this dataset");
        }
        if (isInsert())
        {
            if (!datasetTable.hasPermission(getUser(), InsertPermission.class))
                throw new UnauthorizedException("User does not have permission to insert into this dataset");
        }
        else // !isInsert()
        {
            if (!datasetTable.hasPermission(getUser(), UpdatePermission.class))
                throw new UnauthorizedException("User does not have permission to edit this dataset");
        }

        // if this is our cohort assignment dataset, we may want to display drop-downs for cohort, rather
        // than a text entry box:
        // TODO: This is WRONG! Don't hack on the TableInfo, hack on the View!
        if (!study.isManualCohortAssignment() && Objects.equals(_ds.getDatasetId(), study.getParticipantCohortDatasetId()))
        {
            final List<? extends Cohort> cohorts = CohortService.get().getCohorts(study.getContainer(), getUser());
            String participantCohortPropertyName = study.getParticipantCohortProperty();
            if (participantCohortPropertyName != null)
            {
                BaseColumnInfo cohortCol = (BaseColumnInfo)datasetTable.getColumn(participantCohortPropertyName);
                if (cohortCol != null && cohortCol.getJdbcType() == JdbcType.VARCHAR)
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
                Map<String, Object> formDefaults = new HashMap<>();

                Domain domain = PropertyService.get().getDomain(getContainer(), _ds.getTypeURI());
                if (domain != null)
                {
                    Map<DomainProperty, Object> defaults = DefaultValueService.get().getDefaultValues(getContainer(), domain, getUser());

                    for (Map.Entry<DomainProperty, Object> entry : defaults.entrySet())
                    {
                        if (entry.getValue() != null)
                        {
                            String stringValue = entry.getValue().toString();
                            ColumnInfo temp = entry.getKey().getPropertyDescriptor().createColumnInfo(datasetTable, "LSID", getUser(), getContainer());
                            formDefaults.put(updateForm.getFormFieldName(temp), stringValue);
                        }
                    }
                }

                // for the dataset insert view, allow for URL params with a special prefix to be used as "default" values for the form inputs
                for (Map.Entry<String, String[]> paramEntry : getViewContext().getRequest().getParameterMap().entrySet())
                {
                    if (paramEntry.getKey().startsWith(DEFAULT_INSERT_VALUE_PREFIX))
                        formDefaults.put(paramEntry.getKey().substring(DEFAULT_INSERT_VALUE_PREFIX.length()), paramEntry.getValue());
                }

                ((InsertView) view).setInitialValues(formDefaults);
            }
        }
        DataRegion dataRegion = view.getDataRegion();

        String referer = StringUtils.defaultString(form.getReturnUrl(), HttpView.currentRequest().getHeader("Referer"));
        URLHelper cancelURL;

        if (StringUtils.isEmpty(referer))
        {
            cancelURL = PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(getContainer(), form.getDatasetId());
        }
        else
        {
            cancelURL = new URLHelper(referer);
            dataRegion.addHiddenFormField(ActionURL.Param.returnUrl, referer);
        }

        ButtonBar buttonBar = new ButtonBar();
        buttonBar.setStyle(ButtonBar.Style.separateButtons);
        ActionButton btnSubmit = new ActionButton(new ActionURL(getClass(), getContainer()).addParameter(Dataset.DATASETKEY, form.getDatasetId()), "Submit");
        ActionButton btnCancel = new ActionButton("Cancel", cancelURL);
        buttonBar.add(btnSubmit);
        buttonBar.add(btnCancel);
        buttonBar.setStyle(ButtonBar.Style.separateButtons);
        dataRegion.setButtonBar(buttonBar);

        view.addClientDependency(ClientDependency.fromPath("completion"));

        return view;
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        Study study = getStudy();
        Container container = getContainer();
        ActionURL rootURL;
        Module defaultModule = container.getFolderType().getDefaultModule();
        if (null != defaultModule && "study".equals(defaultModule.getName()))
        {
            rootURL = container.getFolderType().getStartURL(container, getUser());
        }
        else
        {
            rootURL = PageFlowUtil.urlProvider(StudyUrls.class).getBeginURL(container);
        }
        root.addChild(study.getLabel(), rootURL);
        if (null != _ds)
        {
            ActionURL grid = PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(container, _ds.getDatasetId());
            grid.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
            root.addChild(_ds.getLabel(), grid);
            addExtraNavTrail(root);
        }
    }

    protected DataView createNewView(Form form, QueryUpdateForm updateForm, BindException errors)
    {
        if (isInsert())
            return new InsertView(updateForm, errors);
        else
            return new UpdateView(updateForm, errors);
    }

    @Override
    public void validateCommand(Form target, Errors errors) {}

    @Override
    public boolean handlePost(Form form, BindException errors) throws Exception
    {
        int datasetId = form.getDatasetId();
        Study study = getStudy();
        _ds = StudyService.get().getDataset(study.getContainer(), datasetId);
        if (null == _ds)
        {
            redirectTypeNotFound(form.getDatasetId());
            return false;
        }

        TableInfo datasetTable = getQueryTable();
        final Container c = getContainer();
        final User user = getUser();
        if (isInsert())
        {
            if (!datasetTable.hasPermission(user, InsertPermission.class))
                throw new UnauthorizedException("User does not have permission to insert into this dataset");
        }
        else // if (!isInsert())
        {
            if (!datasetTable.hasPermission(user, UpdatePermission.class))
                throw new UnauthorizedException("User does not have permission to edit this dataset");
        }
        if (_ds.isPublishedData())
        {
            throw new UnauthorizedException("This dataset comes from linked data. You cannot update it directly");
        }

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

    @Override
    public ActionURL getSuccessURL(Form form)
    {
        ActionURL url = form.getReturnActionURL();
        if (null != url)
            return url;

        return PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(getContainer(), form.getDatasetId());
    }

    protected Study getStudy()
    {
        if (null == _study)
        {
            _study = StudyService.get().getStudy(getContainer());
            if (null == _study)
            {
                // redirect to the study home page, where admins will see a 'create study' button,
                // and non-admins will simply see a message that no study exists.
                throw new RedirectException(PageFlowUtil.urlProvider(StudyUrls.class).getBeginURL(getContainer()));
            }
        }
        return _study;
    }

    protected void redirectTypeNotFound(int datasetId) throws RedirectException
    {
        throw new RedirectException(PageFlowUtil.urlProvider(StudyUrls.class).getTypeNotFoundURL(getContainer(), datasetId));
    }

    @NotNull
    TableInfo getQueryTable()
    {
        UserSchema schema = StudyService.get().getStudyQuerySchema(getStudy(), getUser());
        // TODO need to return unlocked tableinfo because the action hacks on it
        TableInfo datasetQueryTable= schema.getTable(_ds.getName(), null, true, true);
        if (null == datasetQueryTable) // shouldn't happen...
            throw new NotFoundException("table: study." + _ds.getName());
        return datasetQueryTable;
    }
}
