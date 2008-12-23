/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.experiment;

import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.PrintWriter;
import java.io.Writer;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Oct 20, 2005
 */
public class SampleSetWebPart extends WebPartView<Object>
{
    private String _sampleSetError;
    private final boolean _narrow;

    public SampleSetWebPart(boolean narrow, ViewContext viewContext)
    {
        _narrow = narrow;
        setTitle("Sample Sets");
        setTitleHref(new ActionURL(ExperimentController.ListMaterialSourcesAction.class, viewContext.getContainer()));
    }

    public DataRegion getMaterialSourceWithProjectRegion(ViewContext model) throws Exception
    {
        DataRegion result = getMaterialSourceRegion(model, ExperimentServiceImpl.get().getTinfoMaterialSourceWithProject());
        ActionURL url = new ActionURL(ExperimentController.ListMaterialSourcesAction.class, model.getContainer());
        ColumnInfo containerColumnInfo = ExperimentServiceImpl.get().getTinfoMaterialSourceWithProject().getColumn("Container");
        ContainerDisplayColumn displayColumn = new ContainerDisplayColumn(containerColumnInfo, true, url);
        displayColumn.setEntityIdColumn(containerColumnInfo);
        result.addDisplayColumn(displayColumn);

        if (_narrow)
        {
            result.setButtonBar(new ButtonBar());
            result.getDisplayColumn("container").setVisible(false);
            result.getDisplayColumn("description").setVisible(false);
            result.setShowRecordSelectors(false);
        }
        result.getDisplayColumn("lsid").setVisible(false);
        result.getDisplayColumn("materiallsidprefix").setVisible(false);
        return result;
    }

    public static DataRegion getMaterialSourceRegion(ViewContext model) throws Exception
    {
        return getMaterialSourceRegion(model, ExperimentServiceImpl.get().getTinfoMaterialSource());
    }

    private static DataRegion getMaterialSourceRegion(ViewContext model, TableInfo tableInfo) throws Exception
    {
        DataRegion dr = new DataRegion();
        dr.setName("MaterialsSource");
        dr.setSelectionKey(DataRegionSelection.getSelectionKey(tableInfo.getSchema().getName(), tableInfo.getName(), "SampleSets", dr.getName()));
        dr.addColumns(tableInfo.getUserEditableColumns());
        dr.getDisplayColumn(0).setVisible(false);

        dr.getDisplayColumn("idcol1").setVisible(false);
        dr.getDisplayColumn("idcol2").setVisible(false);
        dr.getDisplayColumn("idcol3").setVisible(false);

        ActionURL url = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, model.getContainer());
        dr.getDisplayColumn(1).setURL(url.toString() + "rowId=${RowId}");
        dr.setShowRecordSelectors(model.hasPermission(ACL.PERM_DELETE) || model.hasPermission(ACL.PERM_UPDATE));
        dr.addDisplayColumn(0, new ActiveSampleSetColumn(model.getContainer()));

        ButtonBar bb = new ButtonBar();

        ActionButton deleteButton = new ActionButton("deleteMaterialSource.view", "Delete Selected", DataRegion.MODE_GRID, ActionButton.Action.GET);
        deleteButton.setDisplayPermission(ACL.PERM_DELETE);
        ActionURL deleteHelper = new ActionURL(ExperimentController.DeleteMaterialSourceAction.class, model.getContainer());
        String script = "return verifySelected(" + dr.getJavascriptFormReference(true) + ", \"" + deleteHelper.getLocalURIString() + "\", \"post\", \"sample set\")";
        deleteButton.setScript(script);
        deleteButton.setActionType(ActionButton.Action.POST);
        bb.add(deleteButton);

        ActionButton uploadMaterialsButton = new ActionButton("showUploadMaterials.view", "Import Sample Set", DataRegion.MODE_GRID, ActionButton.Action.LINK);
        ActionURL uploadURL = new ActionURL(ExperimentController.ShowUploadMaterialsAction.class, model.getContainer());
        uploadMaterialsButton.setURL(uploadURL);
        uploadMaterialsButton.setDisplayPermission(ACL.PERM_UPDATE);
        bb.add(uploadMaterialsButton);
        bb.add(new ActionButton("showUpdateMaterialSource.view", "Update", DataRegion.MODE_DETAILS, ActionButton.Action.GET));
        bb.add(new ActionButton("updateMaterialSource.post", "Submit", DataRegion.MODE_UPDATE));
        bb.add(new ActionButton("listMaterialSources.view", "Show Sample Sets", DataRegion.MODE_DETAILS, ActionButton.Action.LINK));

        ActionURL setAsActiveURL = model.cloneActionURL();
        setAsActiveURL.setAction(ExperimentController.SetActiveSampleSetAction.class);
        ActionButton setAsActiveButton = new ActionButton(setAsActiveURL.toString(), "Set as Active", DataRegion.MODE_GRID | DataRegion.MODE_DETAILS);
        setAsActiveButton.setURL(setAsActiveURL);
        setAsActiveButton.setActionType(ActionButton.Action.POST);
        setAsActiveButton.setDisplayPermission(ACL.PERM_UPDATE);
        bb.add(setAsActiveButton);

        ActionURL showAllURL = model.cloneActionURL();
        showAllURL.setAction(ExperimentController.ShowAllMaterialsAction.class);
        ActionButton showAllButton = new ActionButton(showAllURL.toString(), "Show All Materials", DataRegion.MODE_GRID | DataRegion.MODE_DETAILS);
        showAllButton.setURL(showAllURL);
        showAllButton.setDisplayPermission(ACL.PERM_READ);
        bb.add(showAllButton);

        ActionURL editTypeURL = model.cloneActionURL();
        editTypeURL.setAction(ExperimentController.EditSampleSetTypeAction.class);
        ActionButton editTypeButton = new ActionButton(editTypeURL.toString(), "Edit Property List", DataRegion.MODE_DETAILS);
        editTypeButton.setURL(editTypeURL);
        editTypeButton.setDisplayPermission(ACL.PERM_UPDATE);
        bb.add(editTypeButton);

        dr.setButtonBar(bb);

        return dr;
    }


    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        if (_sampleSetError != null)
        {
            out.write("<font class=\"labkey-error\">" + PageFlowUtil.filter(_sampleSetError) + "</font><br>");
        }
        DataRegion dr = getMaterialSourceWithProjectRegion(getViewContext());
        dr.render(new SampleSetRenderContext(getViewContext()), out);
    }

    public void setSampleSetError(String sampleSetError)
    {
        _sampleSetError = sampleSetError;
    }

    private static final class ActiveSampleSetColumn extends SimpleDisplayColumn
    {
        private final ExpSampleSet _activeSampleSet;

        public ActiveSampleSetColumn(Container c) throws SQLException
        {
            _activeSampleSet = ExperimentService.get().lookupActiveSampleSet(c);
            setCaption("Active");
        }
        
        public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
        {
            renderGridCellContents(ctx, out);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if (_activeSampleSet != null && _activeSampleSet.getLSID().equals(ctx.getRow().get("lsid")))
            {
                out.write("<b>Yes</b>");
            }
            else
            {
                out.write("No");
            }
        }
    }

    public static class SampleSetRenderContext extends RenderContext
    {
        public SampleSetRenderContext(ViewContext context)
        {
            super(context);
        }

        protected SimpleFilter buildFilter(TableInfo tinfo, ActionURL url, String name)
        {
            SimpleFilter result = super.buildFilter(tinfo, url, name);
            result.deleteConditions("Container");
            Object[] params = { getContainer().getProject().getId(), ContainerManager.getSharedContainer().getProject().getId(), ContainerManager.getSharedContainer().getId() };
            result.addWhereClause("(Project = ? OR Project = ? OR Container = ?)", params, "Project");
            return result;
        }
    }
}
