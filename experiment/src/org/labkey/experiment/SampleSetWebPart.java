/*
 * Copyright (c) 2005-2009 LabKey Corporation
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

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.data.*;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.PrintWriter;

/**
 * User: jeckels
 * Date: Oct 20, 2005
 */
public class SampleSetWebPart extends QueryView
{
    private String _sampleSetError;
    private final boolean _narrow;

    public SampleSetWebPart(boolean narrow, ViewContext viewContext)
    {
        super(new ExpSchema(viewContext.getUser(), viewContext.getContainer()));
        _narrow = narrow;
        setSettings(createQuerySettings(viewContext, "SampleSet" + (_narrow ? "Narrow" : "")));
        setTitle("Sample Sets");
        setTitleHref(new ActionURL(ExperimentController.ListMaterialSourcesAction.class, viewContext.getContainer()));
        setShowDetailsColumn(false);

        setShowRecordSelectors(!_narrow);
        if (_narrow)
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            setShowSurroundingBorder(false);
        }
        else
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
            setShowExportButtons(false);
            setShowBorders(true);
            setShadeAlternatingRows(true);
        }
        setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentPlusProjectAndShared);
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx, String dataRegionName)
    {
        QuerySettings settings = new QuerySettings(portalCtx, dataRegionName);
        settings.setSchemaName(getSchema().getSchemaName());
        settings.setAllowChooseQuery(false);
        if (_narrow)
        {
            settings.setViewName("NameOnly");
        }
        settings.setQueryName(ExpSchema.TableType.SampleSets.toString());
        if (settings.getContainerFilterName() == null)
        {
            settings.setContainerFilterName(ContainerFilter.CurrentPlusProjectAndShared.class.getSimpleName());
        }
        return settings;
    }

//    public DataRegion getMaterialSourceWithProjectRegion(ViewContext model) throws Exception
//    {
//        DataRegion result = getMaterialSourceRegion(model, ExperimentServiceImpl.get().getTinfoMaterialSourceWithProject());
//        ActionURL url = new ActionURL(ExperimentController.ListMaterialSourcesAction.class, model.getContainer());
//        ColumnInfo containerColumnInfo = ExperimentServiceImpl.get().getTinfoMaterialSourceWithProject().getColumn("Container");
//        ContainerDisplayColumn displayColumn = new ContainerDisplayColumn(containerColumnInfo, true, url);
//        displayColumn.setEntityIdColumn(containerColumnInfo);
//        result.addDisplayColumn(displayColumn);
//        return result;
//    }
//
    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
    {
        super.populateButtonBar(view, bar, exportAsWebPage);
        populateButtonBar(view.getViewContext(), bar, false);
    }

    public static void populateButtonBar(ViewContext model, ButtonBar bb, boolean detailsView)
    {
        ActionButton deleteButton = new ActionButton("deleteMaterialSource.view", "Delete", DataRegion.MODE_GRID, ActionButton.Action.GET);
        deleteButton.setDisplayPermission(DeletePermission.class);
        ActionURL deleteURL = new ActionURL(ExperimentController.DeleteMaterialSourceAction.class, model.getContainer());
        deleteURL.addParameter("returnURL", model.getActionURL().toString());
        deleteButton.setURL(deleteURL);
        deleteButton.setActionType(ActionButton.Action.POST);
        deleteButton.setRequiresSelection(true);
        bb.add(deleteButton);

        ActionButton uploadMaterialsButton = new ActionButton(ExperimentController.ShowUploadMaterialsAction.class, "Import Sample Set", DataRegion.MODE_GRID, ActionButton.Action.LINK);
        ActionURL uploadURL = new ActionURL(ExperimentController.ShowUploadMaterialsAction.class, model.getContainer());
        uploadMaterialsButton.setURL(uploadURL);
        uploadMaterialsButton.setDisplayPermission(UpdatePermission.class);
        bb.add(uploadMaterialsButton);

        bb.add(new ActionButton(ExperimentController.UpdateMaterialSourceAction.class, "Submit", DataRegion.MODE_UPDATE));

        ActionURL setAsActiveURL = model.cloneActionURL();
        setAsActiveURL.setAction(ExperimentController.SetActiveSampleSetAction.class);
        ActionButton setAsActiveButton = new ActionButton(setAsActiveURL.toString(), "Make Active", DataRegion.MODE_GRID | DataRegion.MODE_DETAILS);
        setAsActiveButton.setURL(setAsActiveURL);
        setAsActiveButton.setActionType(ActionButton.Action.POST);
        setAsActiveButton.setDisplayPermission(UpdatePermission.class);
        setAsActiveButton.setRequiresSelection(!detailsView);
        bb.add(setAsActiveButton);

        ActionURL showAllURL = model.cloneActionURL();
        showAllURL.setAction(ExperimentController.ShowAllMaterialsAction.class);
        ActionButton showAllButton = new ActionButton(showAllURL.toString(), "Show All Materials", DataRegion.MODE_GRID);
        showAllButton.setURL(showAllURL);
        showAllButton.setDisplayPermission(ReadPermission.class);
        bb.add(showAllButton);
    }


    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        if (_sampleSetError != null)
        {
            out.write("<font class=\"labkey-error\">" + PageFlowUtil.filter(_sampleSetError) + "</font><br>");
        }
        super.renderView(model, out);
    }

    public void setSampleSetError(String sampleSetError)
    {
        _sampleSetError = sampleSetError;
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

    protected void setupDataView(DataView ret)
    {
        Sort sort = new Sort("Name");
        ret.getRenderContext().setBaseSort(sort);
        super.setupDataView(ret);
    }

}
