/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.validation.BindException;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * User: jeckels
 * Date: Oct 20, 2005
 */
public class ProtocolWebPart extends WebPartView
{
    private static final String EXPERIMENT_RUN_TYPE = "ExperimentRun";
    private final boolean _narrow;

    public ProtocolWebPart(boolean narrow, ViewContext viewContext)
    {
        super("Protocols");
        _narrow = narrow;
        setTitleHref(new ActionURL(ExperimentController.ShowProtocolGridAction.class, viewContext.getContainer()));
    }

    @Override
    public void renderView(Object model, PrintWriter out) throws Exception
    {
        Container c = getViewContext().getContainer();

        QuerySettings settings = new QuerySettings(getViewContext(), "protocols");
        DataRegion dr = new DataRegion();
        dr.setSettings(settings);
        TableInfo ti = ExperimentServiceImpl.get().getTinfoProtocol();
        List<ColumnInfo> cols = ti.getColumns("RowId,Name,Created");
        dr.setColumns(cols);
        dr.getDisplayColumn(0).setVisible(false);
        dr.getDisplayColumn(1).setURLExpression(new DetailsURL(new ActionURL(ExperimentController.ProtocolDetailsAction.class, c), Collections.singletonMap("rowId","RowId")));
        dr.getDisplayColumn(2).setTextAlign("left");

        if (!_narrow)
        {
            ButtonBar bb = new ButtonBar();

            dr.setShowRecordSelectors(true);

            ActionURL deleteProtUrl = getViewContext().cloneActionURL();
            deleteProtUrl.setAction(ExperimentController.DeleteProtocolByRowIdsAction.class);
            ActionButton deleteProtocol = new ActionButton(deleteProtUrl, "Delete");
            deleteProtocol.setActionType(ActionButton.Action.POST);
            deleteProtocol.setDisplayPermission(DeletePermission.class);
            deleteProtocol.setRequiresSelection(true);
            bb.add(deleteProtocol);

            dr.addHiddenFormField("xarFileName", "ProtocolExport.xar");
            ActionURL exportURL = getViewContext().cloneActionURL();
            exportURL.setAction(ExperimentController.ExportProtocolsAction.class);
            ActionButton exportProtocols = new ActionButton(exportURL, "Export");
            exportProtocols.setActionType(ActionButton.Action.POST);
            exportProtocols.setDisplayPermission(DeletePermission.class);
            exportProtocols.setRequiresSelection(true);
            bb.add(exportProtocols);

            dr.setButtonBar(bb);
        }
        else
        {
            dr.setButtonBar(new ButtonBar());
            dr.getDisplayColumn("Created").setVisible(false);
            dr.setShowSurroundingBorder(false);
        }

        GridView gridView = new GridView(dr, (BindException)null);
        gridView.getRenderContext().setBaseSort(new Sort("Name"));

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ApplicationType"), EXPERIMENT_RUN_TYPE, CompareType.EQUAL);
        gridView.setFilter(filter);
        gridView.setFrame(FrameType.DIV);

        include(gridView);
    }
}
