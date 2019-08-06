package org.labkey.experiment.controllers.exp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.PanelButton;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.experiment.api.ExpSampleSetImpl;
import org.springframework.validation.Errors;

import java.util.List;

public class SampleSetContentsView extends QueryView
{
    private ExpSampleSetImpl _source;

    public SampleSetContentsView(ExpSampleSetImpl source, SamplesSchema schema, QuerySettings settings, Errors errors)
    {
        super(schema, settings, errors);
        _source = source;
        setTitle("Sample Set Contents");
        addClientDependency(ClientDependency.fromPath("Ext4"));
        addClientDependency(ClientDependency.fromPath("experiment/confirmDelete.js"));
    }

    public static ActionButton getDeriveSamplesButton(@NotNull Container container, @Nullable Integer targetSampleSetId)
    {
        ActionURL urlDeriveSamples = new ActionURL(ExperimentController.DeriveSamplesChooseTargetAction.class, container);
        if (targetSampleSetId != null)
            urlDeriveSamples.addParameter("targetSampleSetId", targetSampleSetId);
        ActionButton deriveButton = new ActionButton(urlDeriveSamples, "Derive Samples");
        deriveButton.setActionType(ActionButton.Action.POST);
        deriveButton.setDisplayPermission(InsertPermission.class);
        deriveButton.setRequiresSelection(true);
        return deriveButton;
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
            button.setScript("LABKEY.experiment.confirmDelete('" + getSchema().getName() + "', '" + getQueryDef().getName() + "', '" + getSelectionKey() + "', 'sample', 'samples')");
            button.setRequiresSelection(true);
        }
        return button;
    }

    @Override
    @NotNull
    public PanelButton createExportButton(@Nullable List<String> recordSelectorColumns)
    {
        PanelButton result = super.createExportButton(recordSelectorColumns);
        ActionURL url = new ActionURL(ExperimentController.ExportSampleSetAction.class, getContainer());
        url.addParameter("sampleSetId", _source.getRowId());
        result.addSubPanel("XAR", new JspView<>("/org/labkey/experiment/controllers/exp/exportSampleSetAsXar.jsp", url));
        return result;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        bar.add(getDeriveSamplesButton(getContainer(), _source.getRowId()));
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
