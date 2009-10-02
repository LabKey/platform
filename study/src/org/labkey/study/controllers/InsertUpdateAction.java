/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Writer;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 24, 2009
 */
public abstract class InsertUpdateAction<Form extends DatasetController.EditDatasetRowForm> extends FormViewAction<Form>
{
    protected abstract boolean isInsert();
    protected abstract NavTree appendExtraNavTrail(NavTree root);

    protected InsertUpdateAction(Class<? extends Form> formClass)
    {
        super(formClass);
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
        if (!study.isManualCohortAssignment() && BaseStudyController.safeEquals(ds.getDataSetId(), study.getParticipantCohortDataSetId()))
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
                                            (BaseStudyController.safeEquals(value, cohort.getLabel()) ? "SELECTED" : "") + ">");
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

        /*
This TableInfo is used only when a dataset has been identified as the 'cohort' dataset
for a given study.  In this case, the specified cohort field (which must be of type 'text')
is displayed as an editable drop-down of available cohorts, instead of a text field.
private static class CohortDatasetTableInfo extends StudyDataTableInfo
{
    CohortDatasetTableInfo(DataSetDefinition def, final User user)
    {
        super(def, user);
        StudyImpl study = def.getStudy();
        String cohortProperty = study.getParticipantCohortProperty();
        ColumnInfo cohortCol = getColumn(cohortProperty);
        if (cohortCol != null)
        {
            final Container container = study.getContainer();
            // make the cohort column behave as a drop-down by specifying an FK:
            cohortCol.setFk(new LookupForeignKey("Label")
            {
                public TableInfo getLookupTableInfo()
                {
                    // make the value of the FK be the label, so the correct text is stored in the DB
                    StudyImpl study = StudyManager.getInstance().getStudy(container);
                    return new CohortTable(new StudyQuerySchema(study, user, true))
                    {
                        @Override
                        public List<ColumnInfo> getPkColumns()
                        {
                            ColumnInfo labelCol = getColumn("Label");
                            return Collections.singletonList(labelCol);
                        }
                    };
                }
            });
        }
    }
}
         */


        QueryUpdateForm updateForm = new QueryUpdateForm(datasetTable, getViewContext());

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

        String referer = form.getReturnURL();
        if (referer == null)
            referer = HttpView.currentRequest().getHeader("Referer");

        ActionURL cancelURL;

        if (referer == null)
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
        ActionButton btnSubmit = new ActionButton(new ActionURL(getClass(), getViewContext().getContainer()).addParameter(DataSetDefinition.DATASETKEY, form.getDatasetId()), "Submit");
        ActionButton btnCancel = new ActionButton("Cancel", cancelURL);
        buttonBar.add(btnSubmit);
        buttonBar.add(btnCancel);

        dataRegion.setButtonBar(buttonBar);

        return view;
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
        DataSetDefinition ds = StudyManager.getInstance().getDataSetDefinition(getStudy(), datasetId);
        if (null == ds)
        {
            redirectTypeNotFound(form.getDatasetId());
            return false;
        }
        if (!ds.canWrite(getViewContext().getUser()))
        {
            throw new UnauthorizedException("User does not have permission to edit this dataset");
        }
        if (ds.getProtocolId() != null)
        {
            throw new UnauthorizedException("This dataset comes from an assay. You cannot update it directly");
        }

        TableInfo datasetTable = ds.getTableInfo(getViewContext().getUser());
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
            newLsid = StudyService.get().insertDatasetRow(getViewContext().getUser(), getViewContext().getContainer(), datasetId, data, importErrors);

            // save last inputs for use in default value population:
            Domain domain = PropertyService.get().getDomain(getViewContext().getContainer(), ds.getTypeURI());
            DomainProperty[] properties = domain.getProperties();
            Map<String, Object> requestMap = updateForm.getTypedValues();
            Map<DomainProperty, Object> dataMap = new HashMap<DomainProperty, Object>(requestMap.size());
            for (DomainProperty property : properties)
            {
                ColumnInfo currentColumn = property.getPropertyDescriptor().createColumnInfo(datasetTable, "LSID", getViewContext().getUser());
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
            DefaultValueService.get().setDefaultValues(getViewContext().getContainer(), dataMap, getViewContext().getUser());
        }
        else
        {
            newLsid = StudyService.get().updateDatasetRow(getViewContext().getUser(), getViewContext().getContainer(), datasetId, form.getLsid(), data, importErrors);
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
            StudyManager.getInstance().recomputeStudyDataVisitDate(getStudy());
            StudyManager.getInstance().getVisitManager(getStudy()).updateParticipantVisits(getViewContext().getUser());
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
