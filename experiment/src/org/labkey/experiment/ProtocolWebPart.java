/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpProtocolTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.GridView;
import org.labkey.api.view.ViewContext;
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
public class ProtocolWebPart extends QueryView
{
    private final boolean _narrow;

    public ProtocolWebPart(boolean narrow, ViewContext viewContext)
    {
        super(new ExpSchema(viewContext.getUser(), viewContext.getContainer()));
        _narrow = narrow;
        setTitle("Protocols");
        setTitleHref(new ActionURL(ExperimentController.ShowProtocolGridAction.class, viewContext.getContainer()));

        setSettings(createQuerySettings(viewContext, "protocols"));

        setShowDetailsColumn(false);
        setShowDeleteButtonConfirmationText(false);

        if (_narrow)
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            setShowSurroundingBorder(false);
        }
        else
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
            setShowExportButtons(false);
            setShowBorders(true);
            setShadeAlternatingRows(true);
        }

        setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentAndSubfolders,
                ContainerFilter.Type.CurrentPlusProjectAndShared);
    }

    private QuerySettings createQuerySettings(ViewContext portalCtx, String dataRegionName)
    {
        QuerySettings settings = getSchema().getSettings(portalCtx, dataRegionName);
        settings.setSchemaName(getSchema().getSchemaName());
        if (_narrow)
        {
            settings.setViewName("NameOnly");
        }
        if (settings.getContainerFilterName() == null)
        {
            settings.setContainerFilterName(ContainerFilter.Type.CurrentPlusProjectAndShared.name());
        }
        settings.setQueryName(ExpSchema.TableType.Protocols.toString());
        if (!settings.isMaxRowsSet())
            settings.setMaxRows(20);
        settings.getBaseSort().insertSortColumn("Name");
        settings.getBaseFilter().addCondition(FieldKey.fromParts(ExpProtocolTable.Column.ApplicationType), ExpProtocol.ApplicationType.ExperimentRun.toString());
        return settings;
    }

    protected TableInfo createTable()
    {
        ExpSchema schema = (ExpSchema) getSchema();
        return schema.getTable(ExpSchema.TableType.Protocols);
    }


    protected void populateButtonBar(DataView view, ButtonBar bb)
    {
        if (!_narrow)
        {
            bb.add(createDeleteButton());

            view.getDataRegion().addHiddenFormField("xarFileName", "ProtocolExport.xar");
            ActionURL exportURL = getViewContext().cloneActionURL();
            exportURL.setAction(ExperimentController.ExportProtocolsAction.class);
            ActionButton exportProtocols = new ActionButton(exportURL, "Export");
            exportProtocols.setIconCls("download");
            exportProtocols.setActionType(ActionButton.Action.POST);
            exportProtocols.setDisplayPermission(DeletePermission.class);
            exportProtocols.setRequiresSelection(true);
            bb.add(exportProtocols);
        }
    }

}
