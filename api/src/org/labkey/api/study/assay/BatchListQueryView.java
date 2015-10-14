/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.study.actions.ShowSelectedRunsAction;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;

import java.util.List;

/**
 * User: jeckels
 * Date: Feb 4, 2009
 */
public class BatchListQueryView extends QueryView
{
    private ExpProtocol _protocol;

    public BatchListQueryView(ExpProtocol protocol, AssaySchema schema, QuerySettings settings)
    {
        super(schema, settings);
        _protocol = protocol;
        setShadeAlternatingRows(true);
        setShowBorders(true);
        setShowDetailsColumn(false);
        setShowExportButtons(false);
        setShowDeleteButton(false); // issue 24216
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        ActionURL deleteURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getDeleteExperimentsURL(getContainer(), getReturnURL());
        ActionButton deleteButton = new ActionButton(deleteURL, "Delete");
        deleteButton.setURL(deleteURL);
        deleteButton.setActionType(ActionButton.Action.POST);
        deleteButton.setRequiresSelection(true);
        bar.add(deleteButton);

        ActionURL target = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(getContainer(), _protocol, ShowSelectedRunsAction.class);
        if (getTable().getContainerFilter() != null && getTable().getContainerFilter().getType() != null)
            target.addParameter("containerFilterName", getTable().getContainerFilter().getType().name());
        ActionButton viewSelectedButton = new ActionButton(target, "Show Runs");
        viewSelectedButton.setURL(target);
        viewSelectedButton.setActionType(ActionButton.Action.POST);
        viewSelectedButton.setRequiresSelection(true);
        bar.add(viewSelectedButton);

        List<ActionButton> buttons = AssayService.get().getImportButtons(_protocol, getViewContext().getUser(), getViewContext().getContainer(), false);
        bar.addAll(buttons);
    }

}
