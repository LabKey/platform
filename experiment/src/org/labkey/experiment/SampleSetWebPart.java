/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.controllers.exp.ExperimentController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

        if (_narrow)
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            setShowSurroundingBorder(false);
        }
        else
        {
            setShowExportButtons(false);
            setShowBorders(true);
            setShadeAlternatingRows(true);
        }
        setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentPlusProjectAndShared);
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx, String dataRegionName)
    {
        UserSchema schema = getSchema();
        QuerySettings settings = schema.getSettings(portalCtx, dataRegionName, ExpSchema.TableType.SampleSets.toString());
        if (_narrow)
        {
            settings.setViewName("NameOnly");
        }
        if (settings.getContainerFilterName() == null)
        {
            settings.setContainerFilterName(ContainerFilter.CurrentPlusProjectAndShared.class.getSimpleName());
        }
        settings.getBaseSort().insertSortColumn(FieldKey.fromParts("Name"));
        return settings;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);
        populateButtonBar(view.getViewContext(), bar, false);
    }

    public static void populateButtonBar(ViewContext model, ButtonBar bb, boolean detailsView)
    {
        ActionButton deleteButton = new ActionButton(ExperimentController.DeleteMaterialSourceAction.class, "Delete", DataRegion.MODE_GRID, ActionButton.Action.GET);
        deleteButton.setDisplayPermission(DeletePermission.class);
        ActionURL deleteURL = new ActionURL(ExperimentController.DeleteMaterialSourceAction.class, model.getContainer());
        deleteURL.addParameter(ActionURL.Param.returnUrl, model.getActionURL().toString());
        deleteButton.setIconCls("trash");
        deleteButton.setURL(deleteURL);
        deleteButton.setActionType(ActionButton.Action.POST);
        deleteButton.setRequiresSelection(true);
        bb.add(deleteButton);

        ActionButton uploadMaterialsButton = new ActionButton(ExperimentController.ShowUploadMaterialsAction.class, "Import Sample Set", DataRegion.MODE_GRID, ActionButton.Action.LINK);
        ActionURL uploadURL = new ActionURL(ExperimentController.ShowUploadMaterialsAction.class, model.getContainer());
        uploadMaterialsButton.setURL(uploadURL);
        uploadMaterialsButton.setDisplayPermission(UpdatePermission.class);
        bb.add(uploadMaterialsButton);

        bb.add(new ActionButton(new ActionURL(ExperimentController.UpdateMaterialSourceAction.class, model.getContainer()), "Submit", DataRegion.MODE_UPDATE));

        ActionURL setAsActiveURL = new ActionURL(ExperimentController.SetActiveSampleSetAction.class, model.getContainer());
        ActionButton setAsActiveButton = new ActionButton(setAsActiveURL, "Make Active", DataRegion.MODE_GRID | DataRegion.MODE_DETAILS);
        setAsActiveButton.setActionType(ActionButton.Action.POST);
        setAsActiveButton.setDisplayPermission(UpdatePermission.class);
        setAsActiveButton.setRequiresSelection(!detailsView);
        bb.add(setAsActiveButton);

        ActionURL showAllURL = new ActionURL(ExperimentController.ShowAllMaterialsAction.class, model.getContainer());
        ActionButton showAllButton = new ActionButton(showAllURL, "Show All Materials", DataRegion.MODE_GRID);
        showAllButton.setDisplayPermission(ReadPermission.class);
        bb.add(showAllButton);
    }


    @Override
    protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        PrintWriter out = response.getWriter();
        if (_sampleSetError != null)
        {
            out.write("<font class=\"labkey-error\">" + PageFlowUtil.filter(_sampleSetError) + "</font><br>");
        }
        super.renderView(model, request, response);
    }

    public void setSampleSetError(String sampleSetError)
    {
        _sampleSetError = sampleSetError;
    }

}
