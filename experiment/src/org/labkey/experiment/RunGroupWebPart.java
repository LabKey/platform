/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.view.*;
import org.labkey.api.exp.api.ExpSchema;
import org.labkey.api.exp.api.ExpExperimentTable;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.FieldKey;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Oct 20, 2005
 */
public class RunGroupWebPart extends QueryView
{
    public static final String WEB_PART_NAME = "Run Groups";

    private final boolean _narrow;

    public RunGroupWebPart(ViewContext portalCtx, boolean narrow, String name)
    {
        super(new ExpSchema(portalCtx.getUser(), portalCtx.getContainer()));

        _narrow = narrow;

        setTitle(WEB_PART_NAME);

        setSettings(createQuerySettings(portalCtx, name));

        setShowCustomizeViewLinkInButtonBar(true);
        setShowDetailsColumn(false);

        if (_narrow)
        {
            setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
        }
        else
        {
            setShowRecordSelectors(true);
            setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
            setShowExportButtons(false);
        }
        setTitle("Run Groups");
    }

    public RunGroupWebPart(ViewContext portalCtx, boolean narrow)
    {
        this(portalCtx, narrow, "RunGroup" + (narrow ? "Narrow" : "Wide"));
    }

    public RunGroupWebPart(ViewContext portalCtx, boolean narrow, Portal.WebPart webPart)
    {
        this(portalCtx, narrow, "RunGroup" + (narrow ? "Narrow" : "Wide") + webPart.getIndex());

        showHeader();
    }

    public void showHeader()
    {
        setFrame(FrameType.PORTAL);
        setTitleHref(ExperimentController.ExperimentUrlsImpl.get().getExperimentListURL(getViewContext().getContainer()));
    }


    private QuerySettings createQuerySettings(ViewContext portalCtx, String dataRegionName)
    {
        QuerySettings settings = new QuerySettings(portalCtx.getActionURL(), dataRegionName);
        settings.setSchemaName(getSchema().getSchemaName());
        settings.setAllowChooseQuery(false);
        settings.setQueryName(ExpSchema.EXPERIMENTS_NARROW_WEB_PART_TABLE_NAME);
        return settings;
    }

    protected TableInfo createTable()
    {
        ExpExperimentTable result = (ExpExperimentTable)super.createTable();
        if (_narrow)
        {
            List<FieldKey> fieldKeys = new ArrayList<FieldKey>();
            fieldKeys.add(FieldKey.fromParts(ExpExperimentTable.Column.Name.toString()));
            result.setDefaultVisibleColumns(fieldKeys);
        }
        return result;
    }

    protected void setupDataView(DataView ret)
    {
        Sort sort = new Sort("Name");
        sort.insertSort(new Sort("RowId"));
        ret.getRenderContext().setBaseSort(sort);
        super.setupDataView(ret);
    }

    protected void populateButtonBar(DataView view, ButtonBar bb)
    {
        super.populateButtonBar(view, bb);
        if (!_narrow)
        {
            ActionButton deleteExperiment = new ActionButton("", "Delete Selected");
            ActionURL deleteExpUrl = ExperimentController.ExperimentUrlsImpl.get().getDeleteSelectedExperimentsURL(getViewContext().getContainer(), getViewContext().getActionURL());
            deleteExperiment.setScript("return verifySelected(this.form, \"" + deleteExpUrl.getLocalURIString() + "\", \"post\", \"experiment\")");
            deleteExperiment.setActionType(ActionButton.Action.POST);
            deleteExperiment.setDisplayPermission(ACL.PERM_DELETE);
            bb.add(deleteExperiment);

            ActionButton addXarFile = new ActionButton(ExperimentController.ExperimentUrlsImpl.get().getShowAddXarFileURL(getViewContext().getContainer(), null), "Upload XAR");
            addXarFile.setActionType(ActionButton.Action.LINK);
            addXarFile.setDisplayPermission(ACL.PERM_INSERT);
            bb.add(addXarFile);

            ActionButton createExperiment = new ActionButton(ExperimentController.ExperimentUrlsImpl.get().getCreateRunGroupURL(getViewContext().getContainer(), getViewContext().getActionURL(), false), "Create Run Group");
            createExperiment.setActionType(ActionButton.Action.LINK);
            createExperiment.setDisplayPermission(ACL.PERM_INSERT);
            bb.add(createExperiment);
        }
    }
}
