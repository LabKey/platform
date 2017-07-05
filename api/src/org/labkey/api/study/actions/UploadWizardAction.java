    /*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayColumnInfoRenderer;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayDataCollectorDisplayColumn;
import org.labkey.api.study.assay.AssayHeaderLinkProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.AssayWarningsDisplayColumn;
import org.labkey.api.study.assay.DefaultAssayRunCreator;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.ThawListResolverType;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.writer.ContainerUser;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:01:17 PM
*/
@RequiresPermission(InsertPermission.class)
public class UploadWizardAction<FormType extends AssayRunUploadForm<ProviderType>, ProviderType extends AssayProvider> extends BaseAssayAction<FormType>
{
    protected ExpProtocol _protocol;

    private Map<String, StepHandler<FormType>> _stepHandlers = new HashMap<>();

    protected String _stepDescription;

    public UploadWizardAction()
    {
        this(AssayRunUploadForm.class);
    }

    public UploadWizardAction(Class formClass)
    {
        super(formClass);
        addStepHandler(getBatchStepHandler());
        addStepHandler(getRunStepHandler());
    }

    protected StepHandler<FormType> getBatchStepHandler()
    {
        return new BatchStepHandler();
    }

    protected RunStepHandler getRunStepHandler()
    {
        return new RunStepHandler();
    }

    protected void addStepHandler(StepHandler<FormType> stepHandler)
    {
        _stepHandlers.put(stepHandler.getName(), stepHandler);
    }

    public ModelAndView getView(FormType form, BindException errors) throws Exception
    {
        _protocol = form.getProtocol();
        String currentStep = form.getUploadStep();
        setHelpTopic(new HelpTopic("uploadAssayRuns"));

        if (currentStep == null)
        {
            //FIX: 4014. ensure that the pipeline root path actually exists before starting the first
            //step of the wizard (if it doesn't, the upload will eventually fail)
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
            if (pipeRoot != null && !pipeRoot.isValid())
            {
                StringBuilder msg = new StringBuilder("<p class='labkey-error'>The pipeline directory (");
                msg.append(pipeRoot);
                msg.append(") previously set for this folder does not exist or cannot be reached at this time.");

                //if current user is an admin, include a link to the pipeline setup page
                if (getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
                {
                    ActionURL urlhelper = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(getContainer());
                    msg.append("</p><p><a href='").append(urlhelper.getLocalURIString()).append("'>[Setup Pipeline]</a></p>");
                }
                else
                    msg.append(" Please contact your system administrator.</p>");

                return new HtmlView(msg.toString());
            } //pipe root does not exist
            else
                return getBatchPropertiesView(form, false, errors);
        } //first step

        StepHandler<FormType> handler = _stepHandlers.get(currentStep);
        if (handler == null)
        {
            throw new NotFoundException("Unknown wizard post step: " + currentStep);
        }

        return handler.handleStep(form, errors);
    }

    /** After a run has been successfully created, either send the user to upload another, or complete the wizard */
    protected ModelAndView afterRunCreation(FormType form, ExpRun run, BindException errors) throws ExperimentException
    {
        if (form.isMultiRunUpload())
        {
            form.setSuccessfulUploadComplete(true);
            return getRunPropertiesView(form, false, false, errors);
        }
        else
        {
            throw new RedirectException(getUploadWizardCompleteURL(form, run));
        }
    }

    /** @return the URL to send the user to after they've exited with wizard by successfully uploading their final run in the batch */
    protected ActionURL getUploadWizardCompleteURL(FormType form, ExpRun run)
    {
        if (form.getProvider().isBackgroundUpload(_protocol))
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getShowUploadJobsURL(getContainer(), _protocol, null);
        }
        else
        {
            // if a return url param was specified in the url then return the user to that
            return form.getReturnActionURL(getSummaryLink(_protocol));
        }
    }

    protected InsertView createInsertView(TableInfo baseTable, String lsidCol, List<? extends DomainProperty> properties, boolean errorReshow, String uploadStepName, FormType form, BindException errors)
    {
        // First, find the domain from our domain properties.  We do this, rather than having the caller provide a domain,
        // to allow insert views with a subset a given domain's properties.
        Domain domain = null;
        if (!properties.isEmpty())
        {
            Set<Domain> domains = new HashSet<>();
            for (DomainProperty property : properties)
                domains.add(property.getDomain());
            if (domains.size() > 1)
                throw new IllegalStateException("Insert views cannot be created over properties from multiple domains.");
            domain = domains.iterator().next();
        }

        Map<String, Object> parameterValueMap = ViewServlet.adaptParameterMap(getViewContext().getRequest().getParameterMap());

        try
        {
            Map<DomainProperty, String> runProperties = form.getRunProperties();

            for (DomainProperty dp : runProperties.keySet())
            {
                parameterValueMap.put(dp.getName(), runProperties.get(dp));
            }
        }
        catch(ExperimentException e)
        {
            errors.addError(new ObjectError("main", null, null, e.getMessage()));
        }

        InsertView view = new UploadWizardInsertView(createDataRegionForInsert(baseTable, lsidCol, properties, null), getViewContext(), errors);

        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        if (domain != null)
        {
            try
            {
                defaultValues = form.getDefaultValues(domain);

                // if we have a URL param for a given domain property, use it as the default value
                for (DomainProperty property : domain.getProperties())
                {
                    String paramName = AssayHeaderLinkProvider.PARAM_PREFIX + "." + property.getName();
                    if (parameterValueMap.containsKey(paramName))
                        defaultValues.put(property, parameterValueMap.get(paramName));
                }
            }
            catch (ExperimentException e)
            {
                errors.addError(new ObjectError("main", null, null, e.toString()));
            }
        }

        if (errorReshow)
            view.setInitialValues(parameterValueMap);
        else
        {
            Map<String, Object> inputNameToValue = new HashMap<>();
            for (Map.Entry<DomainProperty, Object> entry : defaultValues.entrySet())
                decodePropertyValues(inputNameToValue, getInputName(entry.getKey()), entry.getValue());
            view.setInitialValues(inputNameToValue);
        }

        // issue 19090: add hidden form field for properties with default value that are hidden from insert view
        for (DomainProperty prop : properties)
        {
            String inputName = getInputName(prop);
            DisplayColumn col = view.getDataRegion().getDisplayColumn(inputName);
            if (col == null && !prop.isShownInInsertView() && defaultValues.get(prop) != null)
                view.getDataRegion().addHiddenFormField(inputName, defaultValues.get(prop).toString());
        }

        if (form.getBatchId() != null)
        {
            view.getDataRegion().addHiddenFormField("batchId", form.getBatchId().toString());
        }
        view.getDataRegion().addHiddenFormField("uploadStep", uploadStepName);
        view.getDataRegion().addHiddenFormField("multiRunUpload", "false");
        view.getDataRegion().addHiddenFormField("severityLevel",((form.getTransformResult().getWarnings() != null)?"ERROR":"WARN"));
        view.getDataRegion().addHiddenFormField("resetDefaultValues", "false");
        view.getDataRegion().addHiddenFormField("rowId", Integer.toString(_protocol.getRowId()));
        view.getDataRegion().addHiddenFormField("uploadAttemptID", form.getUploadAttemptID());
        if (errorReshow)
        {
            // Add unique name of uploaded files as hidden parameters
            for (DomainProperty dp : form.getAdditionalFiles().keySet())
            {
                view.getDataRegion().addHiddenFormField(dp.getName(), form.getAdditionalFiles().get(dp).getName());
            }
        }
        if (form.getReRunId() != null)
        {
            view.getDataRegion().addHiddenFormField("reRunId", form.getReRunId().toString());
        }
        if (form.getReturnUrl() != null)
        {
            view.getDataRegion().addHiddenFormField("returnUrl", form.getReturnUrl());
        }

        DisplayColumn targetStudyCol = view.getDataRegion().getDisplayColumn(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
        if (targetStudyCol != null)
        {
            ColumnInfo col = targetStudyCol.getColumnInfo();
            view.getDataRegion().replaceDisplayColumn(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME,
                    new StudyPickerColumn(col));
        }

        DisplayColumn participantVisitResolverCol = view.getDataRegion().getDisplayColumn(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        if (participantVisitResolverCol != null)
        {
            view.getDataRegion().replaceDisplayColumn(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME,
                    new ParticipantVisitResolverChooser(participantVisitResolverCol.getName(), form.getProvider().getParticipantVisitResolverTypes(),
                            participantVisitResolverCol.getColumnInfo()));
        }

        // Allow registered AssayColumnInfoRenderer to replace display column for the given domain properties
        for (DomainProperty property : properties)
        {
            DisplayColumn displayColumn = view.getDataRegion().getDisplayColumn(property.getName());
            if (displayColumn != null)
            {
                AssayColumnInfoRenderer renderer = AssayService.get().getAssayColumnInfoRenderer(_protocol, displayColumn.getColumnInfo(), getContainer(), getUser());
                if (renderer != null)
                {
                    ColumnInfo columnInfo = displayColumn.getColumnInfo();
                    renderer.fixupColumnInfo(_protocol, columnInfo);
                    view.getDataRegion().replaceDisplayColumn(displayColumn.getName(), columnInfo.getRenderer());
                }
            }
        }

        return view;
    }

    private void decodePropertyValues(Map<String, Object> inputNameToValue, String propName, Object propValue)
    {
        if (propName.equalsIgnoreCase(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME) && propValue != null)
        {
            ParticipantVisitResolverType.Serializer.decode(propValue.toString(), inputNameToValue, propName);
        }
        else
            inputNameToValue.put(propName, propValue);
    }

    private ModelAndView getBatchPropertiesView(FormType runForm, boolean errorReshow, BindException errors) throws ServletException, ExperimentException
    {
        // Check if the user is trying to replace a run that's already been replaced
        if (runForm.getReRun() != null)
        {
            if (runForm.getReRun().getReplacedByRun() != null)
            {
                return new JspView<>("/org/labkey/api/study/actions/alreadyReplacedError.jsp", runForm.getReRun());
            }
        }

        ExpProtocol protocol = runForm.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        runForm.setProviderName(provider.getName());
        Domain uploadDomain = provider.getBatchDomain(protocol);
        if (!showBatchStep(runForm, uploadDomain))
        {
            ActionURL helper = getViewContext().cloneActionURL();
            helper.replaceParameter("uploadStep", BatchStepHandler.NAME);
            throw new RedirectException(helper);
        }
        InsertView insertView = createBatchInsertView(runForm, errorReshow, errors);
        insertView.getDataRegion().setFormActionUrl(new ActionURL(UploadWizardAction.class, getContainer()));
        insertView.setTitle("Batch Properties");
        insertView.setTitlePopupHelp("Batch Properties", "A batch is a set of assay runs, which are usually "
                + "imported at the same time. The batch may have a set of properties, configured by an administrator. "
                + "The values of the properties are the same for all runs within the batch, and are set at import time.");
        // Needed for thaw list participant visit resolvers
        insertView.addClientDependency(ClientDependency.fromPath("sqv"));

        ButtonBar bbar = new ButtonBar();
        bbar.setStyle(ButtonBar.Style.separateButtons);
        addNextButton(bbar);
        addResetButton(runForm, insertView, bbar);
        ActionURL returnURL = runForm.getReturnActionURL();
        addCancelButton(bbar, returnURL);
        insertView.getDataRegion().setButtonBar(bbar, DataRegion.MODE_INSERT);

        JspView<AssayRunUploadForm> assayPropsView = new JspView<>("/org/labkey/study/assay/view/newUploadAssayProperties.jsp", runForm);
        assayPropsView.setTitle("Assay Properties");

        _stepDescription = "Batch Properties";

        return new VBox(assayPropsView, insertView);
    }

    /**
     * Decide whether or not to show the batch properties step in the wizard.
     * @param form the form with posted values
     * @param batchDomain domain for the batch fields
     */
    protected boolean showBatchStep(FormType form, Domain batchDomain) throws ServletException
    {
        return batchDomain != null && !batchDomain.getProperties().isEmpty();
    }

    protected void addNextButton(ButtonBar bbar)
    {
        ActionURL targetURL = getViewContext().getActionURL().clone().deleteParameters();

        // keep any params that start with the assay prefix so they can be used as default values on the next step
        for (Pair<String, String> param : getViewContext().getActionURL().getParameters())
        {
            if (param.getKey().startsWith(AssayHeaderLinkProvider.PARAM_PREFIX + "."))
                targetURL.addParameter(param.getKey(), param.getValue());
        }

        ActionButton newRunButton = new ActionButton(targetURL, "Next", DataRegion.MODE_INSERT, ActionButton.Action.POST);
        newRunButton.setScript("this.className += \" labkey-disabled-button\";", true);
        bbar.add(newRunButton);
    }

    protected void addCancelButton(ButtonBar bbar)
    {
        ActionButton cancelButton = new ActionButton("Cancel", getSummaryLink(_protocol));
        bbar.add(cancelButton);
    }

    protected void addCancelButton(ButtonBar bbar, String returnURL)
    {
        ActionURL link;
        if (returnURL != null && !returnURL.equals(""))
        {
            link = new ActionURL(returnURL);
        }
        else
        {
            link = getSummaryLink(_protocol);
        }
        ActionButton cancelButton = new ActionButton("Cancel", link);
        bbar.add(cancelButton);
    }

    protected void addCancelButton(ButtonBar bbar, ActionURL returnURL)
    {
        ActionURL link;
        if (returnURL != null)
        {
            link = returnURL;
        }
        else
        {
            link = getSummaryLink(_protocol);
        }
        ActionButton cancelButton = new ActionButton("Cancel", link);
        bbar.add(cancelButton);
    }

    protected void addResetButton(FormType newRunForm, InsertView insertView, ButtonBar bbar)
    {
        ActionButton resetDefaultsButton = new ActionButton(getViewContext().cloneActionURL().deleteParameters(), "Reset Default Values");
        resetDefaultsButton.setScript(insertView.getDataRegion().getJavascriptFormReference() + ".resetDefaultValues.value = 'true';", true);
        resetDefaultsButton.setActionType(ActionButton.Action.POST);
        resetDefaultsButton.setId("Btn-ResetDefaultValues");
        bbar.add(resetDefaultsButton);
    }

    protected InsertView createRunInsertView(FormType newRunForm, boolean errorReshow, BindException errors) throws ExperimentException
    {
        List<DomainProperty> propertySet = new ArrayList<>(newRunForm.getRunProperties().keySet());
        return createInsertView(ExperimentService.get().getTinfoExperimentRun(),
                "lsid", propertySet, errorReshow, RunStepHandler.NAME, newRunForm, errors);
    }

    protected InsertView createBatchInsertView(FormType runForm, boolean reshow, BindException errors) throws ExperimentException
    {
        List<DomainProperty> propertySet = new ArrayList<>(runForm.getBatchProperties().keySet());
        return createInsertView(ExperimentService.get().getTinfoExperimentRun(),
                "lsid", propertySet, reshow, BatchStepHandler.NAME, runForm, errors);
    }

    protected ModelAndView getRunPropertiesView(FormType newRunForm, boolean errorReshow, boolean warnings, BindException errors) throws ExperimentException
    {
        // Check if the user is trying to replace a run that's already been replaced
        if (newRunForm.getReRun() != null)
        {
            if (newRunForm.getReRun().getReplacedByRun() != null)
            {
                return new JspView<>("/org/labkey/api/study/actions/alreadyReplacedError.jsp", newRunForm.getReRun());
            }
        }

        if (!errorReshow && !newRunForm.isResetDefaultValues())
        {
            newRunForm.clearUploadedData();
        }

        InsertView insertView = createRunInsertView(newRunForm, errorReshow, errors);

        addHiddenBatchProperties(newRunForm, insertView);

        for (Map.Entry<DomainProperty, String> entry : newRunForm.getBatchProperties().entrySet())
        {
            if (entry.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                ParticipantVisitResolverType resolverType = AbstractAssayProvider.findType(entry.getValue(), newRunForm.getProvider().getParticipantVisitResolverTypes());
                resolverType.addHiddenFormFields(newRunForm, insertView);
                break;
            }
        }

        ExpRunTable table = AssayService.get().createRunTable(_protocol, newRunForm.getProvider(), newRunForm.getUser(), newRunForm.getContainer());
        insertView.getDataRegion().addColumn(0, table.getColumn("Name"));
        insertView.getDataRegion().addColumn(1, table.getColumn("Comments"));

        addSampleInputColumns(newRunForm, insertView);
        if (shouldShowDataCollectorUI(newRunForm))
        {
            insertView.getDataRegion().addDisplayColumn(new AssayDataCollectorDisplayColumn(newRunForm));
        }

        if (warnings)
        {
            insertView.getDataRegion().addDisplayColumn(0, new AssayWarningsDisplayColumn(newRunForm));
        }

        ButtonBar bbar = new ButtonBar();
        bbar.setStyle(ButtonBar.Style.separateButtons);
        addRunActionButtons(newRunForm, insertView, bbar);
        ActionURL returnURL = newRunForm.getReturnActionURL();
        addCancelButton(bbar, returnURL);

        insertView.getDataRegion().setButtonBar(bbar, DataRegion.MODE_INSERT);
        insertView.setTitle("Run Properties");
        insertView.setTitlePopupHelp("Run Properties", "Each run within an assay may have a set of "
                + "properties, configured by an administrator. The values are set at import time.");

        _stepDescription = "Run Properties and Data File";

        VBox vbox = new VBox();

        JspView<AssayRunUploadForm> warningsView = new JspView<>("/org/labkey/api/study/actions/newUploadWarnings.jsp", newRunForm);
        if (newRunForm.getTransformResult().getWarnings() != null)
            warningsView.setTitle("Transform Warnings");
        vbox.addView(warningsView);

        JspView<AssayRunUploadForm> assayPropsView = new JspView<>("/org/labkey/study/assay/view/newUploadAssayProperties.jsp", newRunForm);
        assayPropsView.setTitle("Assay Properties");
        vbox.addView(assayPropsView);

        if (!newRunForm.getBatchProperties().isEmpty())
        {
            JspView<AssayRunUploadForm> batchPropsView = new JspView<>("/org/labkey/study/assay/view/newUploadBatchProperties.jsp", newRunForm);
            batchPropsView.setTitle("Batch Properties");
            vbox.addView(batchPropsView);
        }

        if (newRunForm.isSuccessfulUploadComplete())
            vbox.addView(new HtmlView("<p class=\"labkey-header-large\">Upload successful.  Upload another run below, or click Cancel to view previously uploaded runs.</p>"));

        vbox.addView(insertView);
        return vbox;
    }

    /** Check the assay configuration to determine if we should prompt the user to upload or otherwise specify a data file */
    protected boolean shouldShowDataCollectorUI(FormType newRunForm)
    {
        Domain resultsDomain = newRunForm.getProvider().getResultsDomain(newRunForm.getProtocol());
        return resultsDomain == null || !resultsDomain.getProperties().isEmpty();
    }

    protected void addHiddenBatchProperties(FormType newRunForm, InsertView insertView) throws ExperimentException
    {
        addHiddenProperties(newRunForm.getBatchProperties(), insertView);
    }

    protected void addHiddenRunProperties(FormType newRunForm, InsertView insertView) throws ExperimentException
    {
        addHiddenProperties(newRunForm.getRunProperties(), insertView);
    }

    public static String getInputName(DomainProperty property, String disambiguationId)
    {
        if (disambiguationId != null)
            return ColumnInfo.propNameFromName(disambiguationId + "_" + property.getName());
        else
            return ColumnInfo.propNameFromName(property.getName());
    }

    public static String getInputName(DomainProperty property)
    {
        return getInputName(property, null);
    }

    protected void addHiddenProperties(Map<DomainProperty, String> properties, InsertView insertView)
    {
        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            String name = ColumnInfo.propNameFromName(entry.getKey().getName());
            String value = entry.getValue();
            insertView.getDataRegion().addHiddenFormField(name, value);
        }
    }

    protected void addHiddenProperties(Map<DomainProperty, String> properties, InsertView insertView, String disambiguationId)
    {
        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            String name = getInputName(entry.getKey(), disambiguationId);
            String value = entry.getValue();
            insertView.getDataRegion().addHiddenFormField(name, value);
        }
    }

    protected void addRunActionButtons(FormType newRunForm, InsertView insertView, ButtonBar bbar)
    {
        addFinishButtons(newRunForm, insertView, bbar);
        addResetButton(newRunForm, insertView, bbar);
    }

    protected void addFinishButtons(FormType newRunForm, InsertView insertView, ButtonBar bbar)
    {
        String jsFormReferenceStr = insertView.getDataRegion().getJavascriptFormReference();

        ActionButton saveFinishButton = new ActionButton(getViewContext().getActionURL().clone().deleteParameters(), "Save and Finish");
        saveFinishButton.setScript(jsFormReferenceStr + ".multiRunUpload.value = \"false\"; this.className += \" labkey-disabled-button\";");
        saveFinishButton.setActionType(ActionButton.Action.POST);
        bbar.add(saveFinishButton);

        List<AssayDataCollector> collectors = newRunForm.getProvider().getDataCollectors(Collections.emptyMap(), newRunForm);
        for (AssayDataCollector collector : collectors)
        {
            AssayDataCollector.AdditionalUploadType t = collector.getAdditionalUploadType(newRunForm);
            if (t != AssayDataCollector.AdditionalUploadType.Disallowed)
            {
                ActionButton saveUploadAnotherButton = new ActionButton(getViewContext().getActionURL().clone().deleteParameters(), t.getButtonText());
                saveUploadAnotherButton.setScript(jsFormReferenceStr + ".multiRunUpload.value = \"true\"; this.className += \" labkey-disabled-button\";");
                saveUploadAnotherButton.setActionType(ActionButton.Action.POST);
                bbar.add(saveUploadAnotherButton);
                break;
            }
        }
    }

    protected void addSampleInputColumns(FormType form, InsertView insertView)
    {
        // Don't add any inputs in the base case
    }

    public NavTree appendNavTrail(NavTree root)
    {
        ActionURL helper = getSummaryLink(_protocol);
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        String finalChild = "Data Import";
        if (_stepDescription != null)
        {
            finalChild = finalChild + ": " + _stepDescription;
        }
        result.addChild(finalChild, helper);
        return result;
    }

    // XXX: merge with PublishResultsQueryView.InputColumn and SimpleInputColumn
    protected static class InputDisplayColumn extends SimpleDisplayColumn
    {
        protected String _inputName;

        public InputDisplayColumn(String caption, String inputName)
        {
            _inputName = inputName;
            setCaption(caption);
        }

        public boolean isEditable()
        {
            return true;
        }

        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
        {
            out.write("<input type=\"text\" name=\"" + _inputName + "\" value=\"" + PageFlowUtil.filter(value) + "\">");
        }

        protected Object getInputValue(RenderContext ctx)
        {
            TableViewForm viewForm = ctx.getForm();
            return viewForm.getStrings().get(_inputName);
        }
    }

    public static boolean validateColumnProperties(ContainerUser context, Map<ColumnInfo, String> properties, BindException errors)
    {
        for (ValidationError error : DefaultAssayRunCreator.validateColumnProperties(context, properties))
            errors.reject(SpringActionController.ERROR_MSG, error.getMessage());
        
        return errors.getErrorCount() == 0;
    }

    public static boolean validatePostedProperties(ContainerUser context, Map<DomainProperty, String> properties, BindException errors)
    {
        for (ValidationError error : DefaultAssayRunCreator.validateProperties(context, properties))
            errors.reject(SpringActionController.ERROR_MSG, error.getMessage());

        return errors.getErrorCount() == 0;
    }

    public class BatchStepHandler extends StepHandler<FormType>
    {
        public static final String NAME = "BATCH";

        public ModelAndView handleStep(FormType form, BindException errors) throws ServletException, ExperimentException
        {
            try
            {
                if (!form.isResetDefaultValues() && validatePostedProperties(getViewContext(), form.getBatchProperties(), errors))
                    return getRunPropertiesView(form, false, false, errors);
            }
            catch (ExperimentException e)
            {
                errors.addError(new LabKeyError(e));
            }

            return getBatchPropertiesView(form, !form.isResetDefaultValues(), errors);
        }

        public String getName()
        {
            return NAME;
        }
    }

    public static abstract class StepHandler<StepFormClass extends AssayRunUploadForm>
    {
        public abstract ModelAndView handleStep(StepFormClass form, BindException error) throws ServletException, SQLException, ExperimentException;

        public abstract String getName();
    }

    protected Set<String> getCompletedUploadAttemptIDs()
    {
        Set<String> result = (Set<String>)getViewContext().getRequest().getSession(true).getAttribute("COMPLETE_UPLOAD_ATTEMPT_IDS");
        if (result == null)
        {
            result = new HashSet<>();
            getViewContext().getRequest().getSession(true).setAttribute("COMPLETE_UPLOAD_ATTEMPT_IDS", result);
        }
        return result;
    }

    public class RunStepHandler extends StepHandler<FormType>
    {
        public static final String NAME = "RUN";

        public ModelAndView handleStep(FormType form, BindException errors) throws ServletException, SQLException, ExperimentException
        {
            if (getCompletedUploadAttemptIDs().contains(form.getUploadAttemptID()))
            {
                throw new RedirectException(getViewContext().getActionURL());
            }

            if (!form.isResetDefaultValues() && validatePost(form, errors))
                return handleSuccessfulPost(form, errors);
            else
                return getRunPropertiesView(form, !form.isResetDefaultValues(), false, errors);
        }

        protected ModelAndView handleSuccessfulPost(FormType form, BindException errors) throws ExperimentException
        {
            ExpRun run;
            try
            {
                run = saveExperimentRun(form);
            }
            catch (ValidationException e)
            {
                for (ValidationError error : e.getErrors())
                {
                    if (error instanceof PropertyValidationError)
                        errors.addError(new FieldError("AssayUploadForm", ((PropertyValidationError)error).getProperty(), null, false,
                                new String[]{SpringActionController.ERROR_MSG}, new Object[0], error.getMessage() == null ? error.toString() : error.getMessage()));
                    else
                        errors.reject(SpringActionController.ERROR_MSG, error.getMessage() == null ? error.toString() : error.getMessage());
                }
                return getRunPropertiesView(form, true, false, errors);
            }
            catch (ExperimentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                return getRunPropertiesView(form, true, false, errors);
            }

            return afterRunCreation(form, run, errors);
        }

        public final ExpRun saveExperimentRun(FormType form) throws ExperimentException, ValidationException
        {
            Pair<ExpExperiment, ExpRun> pair = form.getProvider().getRunCreator().saveExperimentRun(form, form.getBatchId());
            assert pair != null && pair.first != null;
            ExpExperiment exp = pair.first;
            ExpRun run = pair.second;

            form.setBatchId(exp.getRowId());
            form.saveDefaultBatchValues();
            form.saveDefaultRunValues();

            // CONSIDER: move into uploadComplete?
            getCompletedUploadAttemptIDs().add(form.getUploadAttemptID());
            form.resetUploadAttemptID();

            return run;
        }

        protected boolean validatePost(FormType form, BindException errors) throws ExperimentException
        {
            try
            {
                return validatePostedProperties(getViewContext(), form.getRunProperties(), errors);
            }
            catch (ExperimentException e)
            {
                errors.addError(new LabKeyError(e));
                return false;
            }
        }

        public String getName()
        {
            return NAME;
        }
    }

    @Nullable
    protected ParticipantVisitResolverType getSelectedParticipantVisitResolverType(AssayProvider provider, AssayRunUploadForm<? extends AssayProvider> newRunForm) throws ExperimentException
    {
        String participantVisitResolverName = null;
        for (Map.Entry<DomainProperty, String> batchProperty : newRunForm.getBatchProperties().entrySet())
        {
            if (batchProperty.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                participantVisitResolverName = batchProperty.getValue();
                break;
            }
        }
        if (participantVisitResolverName != null)
            return AbstractAssayProvider.findType(participantVisitResolverName, provider.getParticipantVisitResolverTypes());
        return null;
    }

    private static class UploadWizardInsertView extends InsertView
    {
        public UploadWizardInsertView(DataRegion dataRegion, ViewContext context, BindException errors)
        {
            super(dataRegion, new WizardRenderContext(context, errors));
        }

        @Override
        protected void _renderDataRegion(RenderContext ctx, Writer out) throws IOException
        {
            // may want to just put this in a js file and include it in all the wizard pages
            out.write("<script type=\"text/javascript\">\n");
            out.write("    function showPopup(elem, txtTitle, txtMsg)\n" +
                    "      {\n" +
                    "        var win = new Ext.Window({\n" +
                    "           title: txtTitle,\n" +
                    "           border: false,\n" +
                    "           constrain: true,\n" +
                    "           html: txtMsg,\n" +
                    "           closeAction:'close',\n" +
                    "           autoScroll: true,\n" +
                    "           modal: true,\n" +
                    "           buttons: [{\n" +
                    "             text: 'Close',\n" +
                    "             id: 'btn_cancel',\n" +
                    "             handler: function(){win.close();}\n" +
                    "           }]\n" +
                    "        });\n" +
                    "        win.show(elem);\n" +
                    "      }");
            out.write("</script>\n");

            super._renderDataRegion(ctx, out);
        }
    }

    private static class WizardRenderContext extends RenderContext
    {
        private static final int MAX_ERRORS = 7;
        public WizardRenderContext(ViewContext context, BindException errors)
        {
            super(context, errors);
        }

        @Override
        public String getErrors(String paramName)
        {
            Errors errors = getErrors();
            if (errors != null && errors.getErrorCount() > MAX_ERRORS)
            {
                List list;
                if ("main".equals(paramName))
                    list = errors.getGlobalErrors();
                else
                    list = errors.getFieldErrors(paramName);
                if (list == null || list.size() == 0)
                    return "";

                Set<String> uniqueErrorStrs = new CaseInsensitiveHashSet();
                StringBuilder sb = new StringBuilder();
                StringBuilder msgBox = new StringBuilder();
                String br = "<font class=\"labkey-error\">";
                int cnt = 0;
                for (Object m : list)
                {
                    String errStr = getViewContext().getMessage((MessageSourceResolvable)m);
                    if (!uniqueErrorStrs.contains(errStr))
                    {
                        if (cnt++ < MAX_ERRORS)
                        {
                            sb.append(br);
                            sb.append(errStr);
                            br = "<br>";
                        }
                        msgBox.append(errStr);
                        msgBox.append("<br>");
                    }
                    uniqueErrorStrs.add(errStr);
                }
                if (sb.length() > 0)
                    sb.append("</font>");

                if (uniqueErrorStrs.size() > MAX_ERRORS)
                {
                    sb.append("<br><a id='extraErrors' href='#' onclick=\"showPopup('extraErrors', 'All Errors', ");
                    sb.append(PageFlowUtil.jsString(msgBox.toString()));
                    sb.append(");return false;\">Too many errors to display (click to show all).<a><br>");
                }
                return sb.toString();
            }
            else
                return super.getErrors(paramName);
        }
    }

    @Override
    public void validate(FormType formType, BindException errors)
    {
        if (ThawListResolverType.NAME.equals(formType.getRequest().getParameter("participantVisitResolver")))
            ThawListResolverType.validationHelper(formType, errors);
    }
}
