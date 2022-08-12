package org.labkey.experiment.controllers.exp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.PanelButton;
import org.labkey.api.exp.api.ExpRunEditor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.springframework.validation.Errors;

import java.util.List;

public class SampleTypeContentsView extends QueryView
{
    private final ExpSampleTypeImpl _source;

    public SampleTypeContentsView(ExpSampleTypeImpl source, SamplesSchema schema, QuerySettings settings, Errors errors)
    {
        super(schema, settings, errors);
        _source = source;
        setTitle("Sample Type Contents");
        addClientDependency(ClientDependency.fromPath("Ext4"));
        addClientDependency(ClientDependency.fromPath("dataregion/confirmDelete.js"));
        setAllowableContainerFilterTypes(
            ContainerFilter.Type.Current,
            ContainerFilter.Type.CurrentAndSubfoldersPlusShared,
            ContainerFilter.Type.CurrentPlusProjectAndShared,
            ContainerFilter.Type.AllFolders
        );
    }

    public static ActionButton getDeriveSamplesButton(@NotNull Container container, @Nullable Integer targetSampleTypeId)
    {
        ActionURL urlDeriveSamples = new ActionURL(ExperimentController.DeriveSamplesChooseTargetAction.class, container);
        if (targetSampleTypeId != null)
            urlDeriveSamples.addParameter("targetSampleTypeId", targetSampleTypeId);
        ActionButton deriveButton = new ActionButton(urlDeriveSamples, "Derive Samples");
        deriveButton.setActionType(ActionButton.Action.POST);
        deriveButton.setDisplayPermission(InsertPermission.class);
        deriveButton.setRequiresSelection(true);
        return deriveButton;
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
        String returnURL = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());

        if (returnURL == null)
        {
            // 27693: Respect returnURL from async webpart requests
            if (getSettings().getReturnURLHelper() != null)
                returnURL = getSettings().getReturnURLHelper().toString();
            else
                returnURL = getViewContext().getActionURL().toString();
        }

        view.getDataRegion().addHiddenFormField(ActionURL.Param.returnUrl, returnURL);
        view.getDataRegion().addHiddenFormField("rowId", String.valueOf(_source.getRowId()));

        return view;
    }

    // Returns null if Study module is not available
    // See method in assay ResultQueryView.getLinktToStudyButton
    @Nullable
    private ActionButton getLinkToStudyButton(DataView view)
    {
        if (null == StudyPublishService.get() || StudyPublishService.get().getValidPublishTargets(getUser(), InsertPermission.class).isEmpty())
            return null;

        StudyUrls urls = PageFlowUtil.urlProvider(StudyUrls.class);
        if (urls == null)
            return null;

        ActionURL publishURL = urls.getLinkToStudyURL(getContainer(), _source);
        for (Pair<String, String> param : publishURL.getParameters())
        {
            if (!"rowId".equalsIgnoreCase(param.getKey()))
                view.getDataRegion().addHiddenFormField(param.getKey(), param.getValue());
        }
        publishURL.deleteParameters();

        ContainerFilter containerFilter = view.getDataRegion().getTable().getContainerFilter();
        if (containerFilter != null && containerFilter.getType() != null)
            publishURL.addParameter("containerFilterName", containerFilter.getType().name());

        ActionButton linkToStudyButton = new ActionButton(publishURL, "Link to Study");
        linkToStudyButton.setDisplayPermission(InsertPermission.class);
        linkToStudyButton.setRequiresSelection(true);
        return linkToStudyButton;
    }

    private String getSelectedScript(ActionURL url, boolean isOuput)
    {
        return "function(data) {" +
                "   var selected = data.selected.join(';');" +
                    DataRegion.getJavaScriptObjectReference(getDataRegionName()) + ".clearSelected({quiet: true});" +
                "   if (selected.length === 0) {" +
                "       window.location = " + PageFlowUtil.jsString(url.getLocalURIString()) + ";" +
                "   }" +
                "   else {" +
                "       window.location = " + PageFlowUtil.jsString(url.getLocalURIString() +
                (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_NO_QUESTION_MARK_URL) ? "?" : "") +
                        (isOuput ? "materialOutputs" : "materialInputs") + "=") + "+encodeURIComponent(selected)" +
                "   }" +
                "}";
    }

    private String getCreateRunScript(ActionURL url, boolean isOutput)
    {
        // Need to figure out selection key
        return DataRegion.getJavaScriptObjectReference(getDataRegionName()) +
            ".getSelected({success: " + getSelectedScript(url, isOutput) + "});";
    }

    public void addCreateRunOptions(@NotNull MenuButton button, @NotNull DataView view, @NotNull ExpRunEditor editor)
    {
        NavTree inputItem = new NavTree("Input  " + editor.getDisplayName() + " samples");
        inputItem.setScript(getCreateRunScript(editor.getEditUrl(view.getViewContext().getContainer()), false));
        button.addMenuItem(inputItem);

        NavTree outputItem = new NavTree("Output " + editor.getDisplayName() + " samples");
        outputItem.setScript(getCreateRunScript(editor.getEditUrl(view.getViewContext().getContainer()), true));
        button.addMenuItem(outputItem);
    }

    @Override
    protected boolean canInsert()
    {
        return _source.canImportMoreSamples() && super.canInsert();
    }

    @Override
    protected boolean canUpdate()
    {
        return _source.canImportMoreSamples() && super.canUpdate();
    }

    @Override
    public ActionButton createDeleteButton()
    {
        // Use default delete button, but without showing the confirmation text
        ActionButton button = super.createDeleteButton();
        if (button != null)
        {
            String dependencyText = "derived sample, job, or assay data dependencies";
            if (ModuleLoader.getInstance().hasModule("samplemanagement"))
                dependencyText += " or status that prevents deletion";
            if (ModuleLoader.getInstance().hasModule("labbook"))
                dependencyText += " or references in one or more active notebooks";
            button.setScript("LABKEY.dataregion.confirmDelete(" +
                    PageFlowUtil.jsString(getDataRegionName()) + ", " +
                    PageFlowUtil.jsString(getSchema().getName())  + ", " +
                    PageFlowUtil.jsString(getQueryDef().getName()) + ", " +
                    "'experiment', 'getMaterialOperationConfirmationData.api', " +
                    PageFlowUtil.jsString(getSelectionKey()) +
                    ", 'sample', 'samples', '" +
                    dependencyText + "', {sampleOperation: 'Delete'})");
            button.setRequiresSelection(true);
        }
        return button;
    }

    @Override
    @NotNull
    public PanelButton createExportButton(@Nullable List<String> recordSelectorColumns)
    {
        PanelButton result = super.createExportButton(recordSelectorColumns);
        ActionURL url = new ActionURL(ExperimentController.ExportSampleTypeAction.class, getContainer());
        url.addParameter("sampleTypeId", _source.getRowId());
        result.addSubPanel("XAR", new JspView<>("/org/labkey/experiment/controllers/exp/exportSampleTypeAsXar.jsp", url));
        return result;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        bar.add(getDeriveSamplesButton(getContainer(), _source.getRowId()));

        ActionButton linkToStudyButton = getLinkToStudyButton(view);
        if (linkToStudyButton != null)
            bar.add(linkToStudyButton);

        // Add run editors
        List<ExpRunEditor> editors = ExperimentService.get().getRunEditors();
        if (!editors.isEmpty())
        {
            MenuButton addRunsButton = new MenuButton("Create Run");
            addRunsButton.setDisplayPermission(InsertPermission.class);

            for (ExpRunEditor editor : editors)
            {
                addCreateRunOptions(addRunsButton, view, editor);
            }

            bar.add(addRunsButton);
        }
    }

    @Override
    public ActionButton createInsertMenuButton(ActionURL overrideInsertUrl, ActionURL overrideImportUrl)
    {
        MenuButton button = new MenuButton("Insert");
        button.setTooltip(getInsertButtonText(INSERT_DATA_TEXT));
        button.setIconCls("plus");
        boolean hasInsertNewOption = false;
        boolean hasImportDataOption = false;

        if (showInsertNewButton())
        {
            ActionURL urlInsert = overrideInsertUrl == null ? urlFor(QueryAction.insertQueryRow) : overrideInsertUrl;
            if (urlInsert != null)
            {
                NavTree insertNew = new NavTree(getInsertButtonText(getInsertButtonText(INSERT_ROW_TEXT)), urlInsert);
                insertNew.setId(getBaseMenuId() + ":Insert:InsertNew");
                button.addMenuItem(insertNew);
                hasInsertNewOption = true;
            }
        }

        if (showImportDataButton())
        {
            ActionURL urlImport = overrideImportUrl == null ? urlFor(QueryAction.importData) : overrideImportUrl;
            if (urlImport != null && urlImport != AbstractTableInfo.LINK_DISABLER_ACTION_URL)
            {
                NavTree importData = new NavTree(getInsertButtonText(IMPORT_BULK_DATA_TEXT), urlImport);
                importData.setId(getBaseMenuId() + ":Insert:Import");
                button.addMenuItem(importData);
                hasImportDataOption = true;
            }
        }

        return hasInsertNewOption && hasImportDataOption ? button : hasInsertNewOption ? createInsertButton() : hasImportDataOption ? createImportButton() : null;
    }
}
