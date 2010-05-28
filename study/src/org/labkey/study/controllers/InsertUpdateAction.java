/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.*;
import org.labkey.study.model.DataSetDefinition;
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
import java.sql.Types;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 24, 2009
 */
public abstract class InsertUpdateAction<Form extends DatasetController.EditDatasetRowForm> extends FormViewAction<Form>
{
    protected abstract boolean isInsert();
    protected abstract NavTree appendExtraNavTrail(NavTree root);
    private QueryUpdateForm _updateForm;

    protected InsertUpdateAction(Class<? extends Form> formClass)
    {
        super(formClass);
    }

    private QueryUpdateForm getUpdateForm(TableInfo datasetTable, BindException errors)
    {
        if (_updateForm == null)
        {
            _updateForm = new QueryUpdateForm(datasetTable, getViewContext(), errors);
        }
        return _updateForm;
    }

    public ModelAndView getView(Form form, boolean reshow, BindException errors) throws Exception
    {
        StudyImpl study = getStudy();
        DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), form.getDatasetId());
        if (null == ds)
        {
            redirectTypeNotFound(form.getDatasetId());
            return null;
        }
        if (!ds.canRead(getViewContext().getUser()))
        {
            throw new UnauthorizedException("User does not have permission to view this dataset");
        }

        TableInfo datasetTable = ds.getTableInfo(getViewContext().getUser());

        // if this is our cohort assignment dataset, we may want to display drop-downs for cohort, rather
        // than a text entry box:
        if (!study.isManualCohortAssignment() && PageFlowUtil.nullSafeEquals(ds.getDataSetId(), study.getParticipantCohortDataSetId()))
        {
            final Cohort[] cohorts = StudyManager.getInstance().getCohorts(study.getContainer(), getViewContext().getUser());
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
                                            (PageFlowUtil.nullSafeEquals(value, cohort.getLabel()) ? "SELECTED" : "") + ">");
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

        QueryUpdateForm updateForm = getUpdateForm(datasetTable, errors);

        DataView view = createNewView(form, updateForm, errors);
        if (isInsert())
        {
            if (!reshow)
            {
                Domain domain = PropertyService.get().getDomain(getViewContext().getContainer(), ds.getTypeURI());
                if (domain != null)
                {
                    Map<DomainProperty, Object> defaults = DefaultValueService.get().getDefaultValues(getViewContext().getContainer(), domain, getViewContext().getUser());
                    Map<String, String> formDefaults = new HashMap<String, String>();
                    for (Map.Entry<DomainProperty, Object> entry : defaults.entrySet())
                    {
                        if (entry.getValue() != null)
                        {
                            String stringValue = entry.getValue().toString();
                            ColumnInfo temp = entry.getKey().getPropertyDescriptor().createColumnInfo(datasetTable, "LSID", getViewContext().getUser());
                            formDefaults.put(updateForm.getFormFieldName(temp), stringValue);
                        }
                    }
                    ((InsertView) view).setInitialValues(formDefaults);
                }
            }
        }
        DataRegion dataRegion = view.getDataRegion();

        ReturnURLString referer = form.getReturnURL();
        if (referer == null || referer.isEmpty())
            referer =  new ReturnURLString(HttpView.currentRequest().getHeader("Referer"));

        ActionURL cancelURL;

        if (referer.isEmpty())
        {
            cancelURL = new ActionURL(StudyController.DatasetAction.class, getViewContext().getContainer());
            cancelURL.addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
        }
        else
        {
            cancelURL = new ActionURL(referer);
            dataRegion.addHiddenFormField("returnURL", referer);
        }

        ButtonBar buttonBar = new ButtonBar();
        buttonBar.setStyle(ButtonBar.Style.separateButtons);
        ActionButton btnSubmit = new ActionButton(new ActionURL(getClass(), getViewContext().getContainer()).addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId()), "Submit");
        ActionButton btnCancel = new ActionButton("Cancel", cancelURL);
        buttonBar.add(btnSubmit);
        buttonBar.add(btnCancel);
        buttonBar.setStyle(ButtonBar.Style.separateButtons);

        dataRegion.setButtonBar(buttonBar);
        return new VBox(new HtmlView("<script type=\"text/javascript\">LABKEY.requiresScript(\"completion.js\");</script>"), view);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        try
        {
            Study study = getStudy();
            root.addChild(study.getLabel(), new ActionURL(StudyController.BeginAction.class, getViewContext().getContainer()));
            root.addChild("Study Overview", new ActionURL(StudyController.OverviewAction.class, getViewContext().getContainer()));
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
        DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        if (null == ds)
        {
            redirectTypeNotFound(form.getDatasetId());
            return false;
        }
        final Container c = getViewContext().getContainer();
        final User user = getViewContext().getUser();
        if (!ds.canWrite(user))
        {
            throw new UnauthorizedException("User does not have permission to edit this dataset");
        }
        if (ds.getProtocolId() != null)
        {
            throw new UnauthorizedException("This dataset comes from an assay. You cannot update it directly");
        }

        TableInfo datasetTable = ds.getTableInfo(getViewContext().getUser());
        QueryUpdateForm updateForm = getUpdateForm(datasetTable, errors);

        if (errors.hasErrors())
            return false;

        // The query DataSet table supports QueryUpdateService while the table returned from ds.getTableInfo() doesn't.
        StudyQuerySchema schema = new StudyQuerySchema(study, user, true);
        TableInfo datasetQueryTable = schema.getDataSetTable(ds);
        QueryUpdateService qus = datasetQueryTable.getUpdateService();
        assert qus != null;

        Map<String,Object> data = updateForm.getDataMap();
        String newLsid;
        if (isInsert())
        {
            List<Map<String, Object>> insertedRows = qus.insertRows(user, c, Collections.singletonList(data));
            if (insertedRows.size() == 0)
                return false;
            Map<String, Object> insertedValues = insertedRows.get(0);
            newLsid = (String)insertedValues.get("lsid");

            // save last inputs for use in default value population:
            Domain domain = PropertyService.get().getDomain(c, ds.getTypeURI());
            DomainProperty[] properties = domain.getProperties();
            Map<String, Object> requestMap = updateForm.getTypedValues();
            Map<DomainProperty, Object> dataMap = new HashMap<DomainProperty, Object>(requestMap.size());
            for (DomainProperty property : properties)
            {
                ColumnInfo currentColumn = property.getPropertyDescriptor().createColumnInfo(datasetTable, "LSID", user);
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
            DefaultValueService.get().setDefaultValues(c, dataMap, user);
        }
        else
        {
            List<Map<String, Object>> updatedRows = qus.updateRows(user, c, Collections.singletonList(data),
                    Collections.singletonList(Collections.<String, Object>singletonMap("lsid", form.getLsid())));
            if (updatedRows.size() == 0)
                return false;
            Map<String, Object> updatedValues = updatedRows.get(0);
            newLsid = (String)updatedValues.get("lsid");
        }

        boolean recomputeCohorts = (!study.isManualCohortAssignment() &&
                PageFlowUtil.nullSafeEquals(datasetId, study.getParticipantCohortDataSetId()));

        // If this results in a change to cohort assignments, the participant ID, or the visit,
        // we need to recompute the participant-visit map:
        if (recomputeCohorts || isInsert() || !newLsid.equals(form.getLsid()))
        {
            StudyManager.getInstance().recomputeStudyDataVisitDate(study, Collections.singleton(ds));
            StudyManager.getInstance().getVisitManager(getStudy()).updateParticipantVisits(user, Collections.singleton(ds));
        }

        return true;
    }

    public ActionURL getSuccessURL(Form form)
    {
        if (form.getReturnURL() != null)
            return new ActionURL(form.getReturnURL());

        ActionURL url = new ActionURL(StudyController.DatasetAction.class, getViewContext().getContainer());
        url.addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId());
        if (StudyManager.getInstance().showQCStates(getViewContext().getContainer()))
        {
            QCStateSet stateSet = QCStateSet.getAllStates(getViewContext().getContainer());
            url.addParameter(BaseStudyController.SharedFormParameters.QCState, stateSet.getFormValue());
        }
        return url;
    }

    protected StudyImpl getStudy() throws ServletException
    {
        return BaseStudyController.getStudy(false, getViewContext().getContainer());
    }

    protected void redirectTypeNotFound(int datasetId) throws RedirectException
    {
        HttpView.throwRedirect(new ActionURL(StudyController.TypeNotFoundAction.class, getViewContext().getContainer()).addParameter("id", datasetId));
    }
}
