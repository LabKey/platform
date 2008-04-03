package org.labkey.api.study.actions;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.*;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.action.SpringActionController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMessage;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.ConversionException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.sql.SQLException;
import java.io.Writer;
import java.io.IOException;
import java.io.File;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:01:17 PM
*/
@RequiresPermission(ACL.PERM_INSERT)
public class UploadWizardAction<FormClass extends AssayRunUploadForm> extends BaseAssayAction<FormClass>
{
    protected ExpProtocol _protocol;

    private Map<String, StepHandler> _stepHandlers = new HashMap<String, StepHandler>();

    protected String _stepDescription;

    public UploadWizardAction()
    {
        this((Class<FormClass>)AssayRunUploadForm.class);
    }

    public UploadWizardAction(Class<FormClass> formClass)
    {
        super(formClass);
        addStepHandler(getUploadSetStepHandler());
        addStepHandler(getRunStepHandler());
    }

    protected StepHandler getUploadSetStepHandler()
    {
        return new UploadSetStepHandler();
    }

    protected StepHandler getRunStepHandler()
    {
        return new RunStepHandler();
    }

    protected void addStepHandler(StepHandler stepHandler)
    {
        _stepHandlers.put(stepHandler.getName(), stepHandler);
    }

    public ModelAndView getView(FormClass form, BindException errors) throws Exception
    {
        _protocol = form.getProtocol();
        String currentStep = form.getUploadStep();

        if (currentStep == null)
        {
            //FIX: 4014. ensure that the pipeline root path actually exists before starting the first
            //step of the wizard (if it doesn't, the upload will eventually fail)
            File root = PipelineService.get().findPipelineRoot(getContainer()).getRootPath();
            if(!NetworkDrive.exists(root)) //NetworkDrive.exists() will ensure that a \\server\share path gets mounted
            {
                StringBuilder msg = new StringBuilder("<p class='labkey-error'>The pipeline directory (");
                msg.append(root.getAbsolutePath());
                msg.append(") previously set for this folder does not exist or cannot be reached at this time.");

                //if current user is an admin, include a link to the pipeline setup page
                if(getViewContext().getUser().isAdministrator())
                {
                    ActionURL urlhelper = new ActionURL("Pipeline", "setup", getContainer());
                    msg.append("</p><p><a href='").append(urlhelper.getLocalURIString()).append("'>[Setup Pipeline]</a></p>");
                }
                else
                    msg.append(" Please contact your system administrator.</p>");

                return new HtmlView(msg.toString());
            } //pipe root does not exist
            else
                return getUploadSetPropertiesView(form, false, errors);
        } //first step

        StepHandler handler = _stepHandlers.get(currentStep);
        if (handler == null)
        {
            throw new IllegalStateException("Unknown wizard post step: " + currentStep);
        }

        return handler.handleStep(form, errors);
    }

    protected ModelAndView afterRunCreation(FormClass form, ExpRun run, BindException errors) throws ServletException, SQLException
    {
        return runUploadComplete(form, errors);
    }

    protected ModelAndView runUploadComplete(FormClass form, BindException errors)
            throws ServletException
    {
        if (form.isMultiRunUpload())
        {
            form.setSuccessfulUploadComplete(true);
            return getRunPropertiesView(form, false, false, errors);
        }
        else
        {
            HttpView.throwRedirect(getSummaryLink(_protocol));
            return null;
        }
    }

    protected InsertView createInsertView(TableInfo baseTable, String lsidCol, Map<PropertyDescriptor, String> propertyDescriptors, boolean reshow, boolean resetDefaultValues, String uploadStepName, FormClass form, BindException errors)
    {
        PropertyDescriptor[] pds = new PropertyDescriptor[propertyDescriptors.size()];
        int i = 0;
        for (PropertyDescriptor pd : propertyDescriptors.keySet())
            pds[i++] = pd;

        InsertView view = new InsertView(createDataRegion(baseTable, lsidCol, pds, null, uploadStepName), errors);
        if (resetDefaultValues)
        {
            clearDefaultValues(uploadStepName);
        }
        else if (reshow)
        {
            view.setInitialValues(getViewContext().getRequest().getParameterMap());
        }
        else
        {
            try
            {
                view.setInitialValues(getDefaultValues(uploadStepName, form));
            }
            catch (ExperimentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }
        }
        view.getDataRegion().addHiddenFormField("uploadStep", uploadStepName);
        view.getDataRegion().addHiddenFormField("multiRunUpload", "false");
        view.getDataRegion().addHiddenFormField("resetDefaultValues", "false");
        view.getDataRegion().addHiddenFormField("rowId", Integer.toString(_protocol.getRowId()));
        view.getDataRegion().addHiddenFormField("providerName", AssayService.get().getProvider(_protocol).getName());
        view.getDataRegion().addHiddenFormField("uploadAttemptID", form.getUploadAttemptID());

        DisplayColumn targetStudyCol = view.getDataRegion().getDisplayColumn(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
        if (targetStudyCol != null)
        {
            view.getDataRegion().replaceDisplayColumn(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME,
                    new StudyPickerColumn(targetStudyCol.getColumnInfo()));
        }

        DisplayColumn participantVisitResolverCol = view.getDataRegion().getDisplayColumn(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        if (participantVisitResolverCol != null)
        {
            view.getDataRegion().replaceDisplayColumn(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME,
                    new ParticipantVisitResolverChooser(participantVisitResolverCol.getName(), form.getProvider().getParticipantVisitResolverTypes()));
        }

        return view;
    }

    private String getPropertySetName(String suffix)
    {
        return "Assay" + _protocol.getRowId() + "-" + suffix;
    }

    protected void clearDefaultValues(String suffix)
    {
        Map<String, String> properties = PropertyManager.getWritableProperties(getViewContext().getUser().getUserId(),
                        getViewContext().getContainer().getId(), getPropertySetName(suffix), false);
        if (properties != null)
        {
            properties.clear();
            PropertyManager.saveProperties(properties);
        }
    }

    protected Map<String, String> getDefaultValues(String suffix, FormClass form) throws ExperimentException
    {
        ViewContext context = getViewContext();
        Map<String, String> result = PropertyManager.getProperties(context.getUser().getUserId(),
                        context.getContainer().getId(), getPropertySetName(suffix), false);
        return result == null ? Collections.<String, String>emptyMap() : result;
    }

    protected void saveDefaultValues(Map<PropertyDescriptor, String> values, HttpServletRequest request, AssayProvider provider, String suffix)
    {
        Map<String, String> properties = PropertyManager.getWritableProperties(getViewContext().getUser().getUserId(),
                        getViewContext().getContainer().getId(), getPropertySetName(suffix), true);
        for (Map.Entry<PropertyDescriptor, String> entry : values.entrySet())
        {
            PropertyDescriptor pd = entry.getKey();
            String value = entry.getValue();
            properties.put(ColumnInfo.propNameFromName(pd.getName()), value);
            if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(pd.getName()) && value instanceof String)
            {
                ParticipantVisitResolverType type = AbstractAssayProvider.findType(value, provider.getParticipantVisitResolverTypes());
                type.putDefaultProperties(request, properties);
            }
        }
        PropertyManager.saveProperties(properties);
    }

    private ModelAndView getUploadSetPropertiesView(FormClass runForm, boolean reshow, BindException errors) throws ServletException
    {
        ExpProtocol protocol = getProtocol(runForm);
        AssayProvider provider = AssayService.get().getProvider(protocol);
        runForm.setProviderName(provider.getName());
        PropertyDescriptor[] uploadSetColumns = provider.getUploadSetColumns(protocol);

        if (uploadSetColumns == null || uploadSetColumns.length == 0)
        {
            ActionURL helper = getViewContext().cloneActionURL();
            helper.addParameter("uploadStep", UploadSetStepHandler.NAME);
            helper.addParameter("providerName", runForm.getProviderName());
            HttpView.throwRedirect(helper);
        }
        InsertView insertView = createInsertView(ExperimentService.get().getTinfoExperimentRun(),
                "lsid", runForm.getUploadSetProperties(), reshow, runForm.isResetDefaultValues(), UploadSetStepHandler.NAME, runForm, errors);

        ButtonBar bbar = new ButtonBar();
        addNextButton(bbar);
        addResetButton(runForm, insertView, bbar);
        addCancelButton(bbar);
        insertView.getDataRegion().setButtonBar(bbar, DataRegion.MODE_INSERT);

        insertView.setTitle("Upload Set Properties");

        _stepDescription = "Upload Set Properties";

        JspView<AssayRunUploadForm> headerView = new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/newUploadSet.jsp", runForm);
        return new VBox(headerView, insertView);
    }

    protected void addNextButton(ButtonBar bbar)
    {
        ActionButton newRunButton = new ActionButton(getViewContext().getActionURL().getAction() + ".view", "Next",
                DataRegion.MODE_INSERT, ActionButton.Action.POST);
        bbar.add(newRunButton);
    }

    protected void addCancelButton(ButtonBar bbar)
    {
        ActionButton cancelButton = new ActionButton("Cancel", getSummaryLink(_protocol));
        bbar.add(cancelButton);
    }

    protected void addResetButton(FormClass newRunForm, InsertView insertView, ButtonBar bbar)
    {
        ActionButton resetDefaultsButton = new ActionButton(getViewContext().getActionURL().getAction() + ".view", "Reset Default Values");
        resetDefaultsButton.setScript("this.form.action=\"" + getViewContext().getActionURL().getAction() + ".view" + "\"; document.forms." + insertView.getDataRegion().getName() + ".resetDefaultValues.value = \"true\";");
        resetDefaultsButton.setActionType(ActionButton.Action.POST);
        bbar.add(resetDefaultsButton);
    }

    protected InsertView createRunInsertView(FormClass newRunForm, boolean reshow, BindException errors)
    {
        return createInsertView(ExperimentService.get().getTinfoExperimentRun(),
                "lsid", newRunForm.getRunProperties(), reshow, newRunForm.isResetDefaultValues(), RunStepHandler.NAME, newRunForm, errors);
    }

    private ModelAndView getRunPropertiesView(FormClass newRunForm, boolean reshow, boolean warnings, BindException errors)
    {
        InsertView insertView = createRunInsertView(newRunForm, reshow, errors);
        addHiddenUploadSetProperties(newRunForm, insertView);

        for (Map.Entry<PropertyDescriptor, String> entry : newRunForm.getUploadSetProperties().entrySet())
        {
            if (entry.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                ParticipantVisitResolverType resolverType = AbstractAssayProvider.findType(entry.getValue(), newRunForm.getProvider().getParticipantVisitResolverTypes());
                resolverType.addHiddenFormFields(insertView, newRunForm);
                break;
            }
        }

        insertView.getDataRegion().addColumn(0, ExperimentService.get().getTinfoExperimentRun().getColumn("Name"));
        insertView.getDataRegion().addColumn(1, ExperimentService.get().getTinfoExperimentRun().getColumn("Comments"));

        addSampleInputColumns(getProtocol(newRunForm), insertView);
        if (!reshow)
        {
            newRunForm.clearUploadedData();
        }
        insertView.getDataRegion().addColumn(new AssayDataCollectorDisplayColumn(newRunForm));

        if (warnings)
        {
            insertView.getDataRegion().addColumn(0, new AssayWarningsDisplayColumn(newRunForm));
        }

        ButtonBar bbar = new ButtonBar();

        addRunActionButtons(newRunForm, insertView, bbar);
        addCancelButton(bbar);

        insertView.getDataRegion().setButtonBar(bbar, DataRegion.MODE_INSERT);
        insertView.setTitle("Run Properties");

        _stepDescription = "Run Properties and Upload";

        JspView<AssayRunUploadForm> headerView = new JspView<AssayRunUploadForm>("/org/labkey/api/study/actions/newRunProperties.jsp", newRunForm);
        return new VBox(headerView, insertView);
    }

    protected void addHiddenUploadSetProperties(AssayRunUploadForm newRunForm, InsertView insertView)
    {
        addHiddenProperties(newRunForm.getUploadSetProperties(), insertView);
    }

    protected void addHiddenRunProperties(AssayRunUploadForm newRunForm, InsertView insertView)
    {
        addHiddenProperties(newRunForm.getRunProperties(), insertView);
    }

    protected void addHiddenProperties(Map<PropertyDescriptor, String> properties, InsertView insertView)
    {
        for (Map.Entry<PropertyDescriptor, String> entry : properties.entrySet())
        {
            String name = ColumnInfo.propNameFromName(entry.getKey().getName());
            String value = entry.getValue();
            insertView.getDataRegion().addHiddenFormField(name, value);
        }
    }

    protected void addRunActionButtons(FormClass newRunForm, InsertView insertView, ButtonBar bbar)
    {
        addFinishButtons(newRunForm, insertView, bbar);
        addResetButton(newRunForm, insertView, bbar);
    }

    protected void addFinishButtons(FormClass newRunForm, InsertView insertView, ButtonBar bbar)
    {
        ActionButton saveFinishButton = new ActionButton(getViewContext().getActionURL().getAction() + ".view", "Save and Finish");
        saveFinishButton.setScript("document.forms." + insertView.getDataRegion().getName() + ".multiRunUpload.value = \"false\";");
        saveFinishButton.setActionType(ActionButton.Action.POST);
        bbar.add(saveFinishButton);

        List<AssayDataCollector> collectors = newRunForm.getProvider().getDataCollectors(Collections.<String, File>emptyMap());
        for (AssayDataCollector collector : collectors)
        {
            if (collector.allowAdditionalUpload(newRunForm))
            {
                ActionButton saveUploadAnotherButton = new ActionButton(getViewContext().getActionURL().getAction() + ".view", "Save and Upload Another Run");
                saveUploadAnotherButton.setScript("document.forms." + insertView.getDataRegion().getName() + ".multiRunUpload.value = \"true\";");
                saveUploadAnotherButton.setActionType(ActionButton.Action.POST);
                bbar.add(saveUploadAnotherButton);
                break;
            }
        }
    }

    protected void addSampleInputColumns(ExpProtocol protocol, InsertView insertView)
    {
        // Don't add any inputs in the base case
    }

    public NavTree appendNavTrail(NavTree root)
    {
        ActionURL helper = getSummaryLink(_protocol);
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), AssayService.get().getAssayRunsURL(getContainer(), _protocol));
        String finalChild = "Data Upload";
        if (_stepDescription != null)
        {
            finalChild = finalChild + ": " + _stepDescription;
        }
        result.addChild(finalChild, helper);
        return result;
    }

    private boolean validateSamples(Map<String, String> sampleFormElements, String targetStudy) throws SQLException
    {
/*        if (targetStudy == null)
            return true;
        Container studyContainer = ContainerManager.getForId(targetStudy);
        if (studyContainer == null)
            return true;
        Map<Container, String> studies = AssayPublishService.get().getValidPublishTargets(getViewContext().getUser());
        if (!studies.containsKey(studyContainer))
            return true;
        ActionErrors strutsErrors = PageFlowUtil.getActionErrors(getViewContext().getRequest(), true);
        for (Map.Entry<String,String> sampleFormElement : sampleFormElements.entrySet())
        {
            AssayPublishService.SampleInfo info = AssayPublishService.get().getSampleInfo(studyContainer, sampleFormElement.getValue());
            if (info == null || info.getParticipantId() == null || info.getSequenceNum() == null)
            {
                strutsErrors.add("main", new ActionMessage("Sample \"" + sampleFormElement.getValue() +
                        "\" was not found in study \"" + studies.get(studyContainer) + "\""));
            }
        }
        return strutsErrors.size() == 0;
        */
        // Don't validate samples for now - we don't actually do anything useful with them
        return true;
    }

    protected class InputDisplayColumn extends SimpleDisplayColumn
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

    private class StudyPickerColumn extends InputDisplayColumn
    {
        ColumnInfo _colInfo;

        public StudyPickerColumn(ColumnInfo col)
        {
            super("Target Study", "targetStudy");
            _colInfo = col;
        }

        public void renderDetailsCaptionCell(RenderContext ctx, Writer out) throws IOException
        {
            if (null == _caption)
                return;

            out.write("<td class='ms-searchform'>");
            renderTitle(ctx, out);
            int mode = ctx.getMode();
            if (mode == DataRegion.MODE_INSERT || mode == DataRegion.MODE_UPDATE)
            {
                if (_colInfo != null)
                {
                    String helpPopupText = ((_colInfo.getFriendlyTypeName() != null) ? "Type: " + _colInfo.getFriendlyTypeName() + "\n" : "") +
                                ((_colInfo.getDescription() != null) ? "Description: " + _colInfo.getDescription() + "\n" : "");
                    out.write(PageFlowUtil.helpPopup(_colInfo.getName(), helpPopupText));
                    if (!_colInfo.isNullable())
                        out.write(" *");
                }
            }
            out.write("</td>");
        }

        public void renderDetailsData(RenderContext ctx, Writer out, int span) throws IOException, SQLException
        {
            super.renderDetailsData(ctx, out, 1);
        }

        public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
        {
            Map<Container, String> studies = AssayPublishService.get().getValidPublishTargets(ctx.getViewContext().getUser(), ACL.PERM_READ);

            out.write("<select name=\"" + _inputName + "\">\n");
            out.write("    <option value=\"\">[None]</option>\n");
            for (Map.Entry<Container, String> entry : studies.entrySet())
            {
                Container container = entry.getKey();
                out.write("    <option value=\"" + PageFlowUtil.filter(container.getId()) + "\"");
                if (container.getId().equals(value))
                    out.write(" SELECTED");
                out.write(">" + PageFlowUtil.filter(container.getPath() + " (" + entry.getValue()) + ")</option>\n");
            }
            out.write("</select>");
        }

        public ColumnInfo getColumnInfo()
        {
            return _colInfo;
        }

        public boolean isQueryColumn()
        {
            return true;
        }
    }

    public static boolean validatePostedProperties(Map<PropertyDescriptor, String> properties, HttpServletRequest request, BindException errors)
    {
        for (Map.Entry<PropertyDescriptor, String> entry : properties.entrySet())
        {
            PropertyDescriptor pd = entry.getKey();
            String value = entry.getValue();
            boolean missing = (value == null || value.length() == 0);
            if (pd.isRequired() && missing)
            {
                errors.reject(SpringActionController.ERROR_MSG,
                        pd.getNonBlankLabel() + " is required and must be of type " + ColumnInfo.getFriendlyTypeName(pd.getPropertyType().getJavaType()) + ".");
            }
            else if (!missing)
            {
                try
                {
                    ConvertUtils.convert(value, pd.getPropertyType().getJavaType());
                }
                catch (ConversionException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG,
                            pd.getNonBlankLabel() + " must be of type " + ColumnInfo.getFriendlyTypeName(pd.getPropertyType().getJavaType()) + ".");
                }
            }
        }
        return errors.getErrorCount() == 0;
    }

    public class UploadSetStepHandler extends StepHandler<FormClass>
    {
        public static final String NAME = "UPLOAD_SET";

        public ModelAndView handleStep(FormClass form, BindException errors) throws ServletException
        {
            if (!form.isResetDefaultValues() && validatePostedProperties(form.getUploadSetProperties(), form.getRequest(), errors))
                return getRunPropertiesView(form, false, false, errors);
            else
                return getUploadSetPropertiesView(form, true, errors);
        }

        public String getName()
        {
            return NAME;
        }
    }

    public static abstract class StepHandler<StepFormClass extends AssayRunUploadForm>
    {
        public abstract ModelAndView handleStep(StepFormClass form, BindException error) throws ServletException, SQLException;

        public abstract String getName();
    }

    protected Set<String> getCompletedUploadAttemptIDs()
    {
        Set<String> result = (Set<String>)getViewContext().getRequest().getSession(true).getAttribute("COMPLETE_UPLOAD_ATTEMPT_IDS");
        if (result == null)
        {
            result = new HashSet<String>();
            getViewContext().getRequest().getSession(true).setAttribute("COMPLETE_UPLOAD_ATTEMPT_IDS", result);
        }
        return result;
    }
    
    public class RunStepHandler extends StepHandler<FormClass>
    {
        public static final String NAME = "RUN";

        public ModelAndView handleStep(FormClass form, BindException errors) throws ServletException, SQLException
        {
            boolean validSamples = true;
            if (!form.isIgnoreWarnings())
                validSamples = validateSamples(form.getRunSamplesByFormElementName(), form.getTargetStudy());

            if (getCompletedUploadAttemptIDs().contains(form.getUploadAttemptID()))
            {
                HttpView.throwRedirect(AssayService.get().getUploadWizardURL(getContainer(), _protocol));
            }

            if (!form.isResetDefaultValues() && validatePost(form, errors) && validSamples)
                return handleSuccessfulPost(form, errors);
            else
                return getRunPropertiesView(form, true, !validSamples, errors);
        }

        protected ModelAndView handleSuccessfulPost(FormClass form, BindException errors) throws SQLException, ServletException
        {
            ExpRun run;
            try
            {
                run = saveExperimentRun(form);

                saveDefaultValues(form.getUploadSetProperties(), form.getRequest(), form.getProvider(), UploadSetStepHandler.NAME);
                saveDefaultValues(form.getRunProperties(), form.getRequest(), form.getProvider(), RunStepHandler.NAME);
                getCompletedUploadAttemptIDs().add(form.getUploadAttemptID());
                AssayDataCollector collector = form.getSelectedDataCollector();
                if (collector != null)
                {
                    collector.uploadComplete(form);
                }
                form.resetUploadAttemptID();
            }
            catch (ExperimentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                return getRunPropertiesView(form, true, false, errors);
            }

            return afterRunCreation(form, run, errors);
        }

        protected ExpRun saveExperimentRun(FormClass form) throws ExperimentException
        {
            AssayProvider provider = form.getProvider();
            return provider.saveExperimentRun(form);
        }

        protected boolean validatePost(FormClass form, BindException errors)
        {
            return UploadWizardAction.this.validatePostedProperties(form.getRunProperties(), form.getRequest(), errors);
        }

        public String getName()
        {
            return NAME;
        }
    }

    protected ParticipantVisitResolverType getSelectedParticipantVisitResolverType(AssayProvider provider, AssayRunUploadForm newRunForm)
    {
        String participantVisitResolverName = null;
        for (Map.Entry<PropertyDescriptor, String> uploadSetProperty : newRunForm.getUploadSetProperties().entrySet())
        {
            if (uploadSetProperty.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                participantVisitResolverName = uploadSetProperty.getValue();
                break;
            }
        }
        if (participantVisitResolverName != null)
            return AbstractAssayProvider.findType(participantVisitResolverName, provider.getParticipantVisitResolverTypes());
        return null;
    }
}
