/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.pipeline.status;

import org.labkey.api.action.ApiAction;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.api.PipelineQuerySchema;
import org.springframework.validation.BindException;

/**
 * User: jeckels
 * Date: Dec 21, 2009
 */
public class PipelineQueryView extends QueryView
{
    private final ViewContext _context;
    private final Class<? extends ApiAction> _apiAction;
    private final boolean _minimal;

    public PipelineQueryView(ViewContext context, BindException errors, Class<? extends ApiAction> apiAction, boolean minimal)
    {
        super(new PipelineQuerySchema(context.getUser(), context.getContainer()), new QuerySettings(context, "StatusFiles", "job"), errors);
        _minimal = minimal;
        getSettings().setAllowChooseQuery(false);
        _context = context;
        _apiAction = apiAction;

        setShadeAlternatingRows(true);
        setShowBorders(true);

        setButtonBarPosition(minimal ? DataRegion.ButtonBarPosition.BOTTOM : DataRegion.ButtonBarPosition.BOTH);

        setShowRecordSelectors(!minimal && (getContainer().hasPermission(getUser(), UpdatePermission.class) || getContainer().isRoot()));
    }

    @Override
    protected DataRegion createDataRegion()
    {
        StatusDataRegion rgn = new StatusDataRegion();
        configureDataRegion(rgn);
        rgn.setApiAction(_apiAction);
        return rgn;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        if (_minimal)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("Status", PipelineJob.COMPLETE_STATUS, CompareType.NEQ);
            view.getRenderContext().setBaseFilter(filter);
        }

        view.getRenderContext().setBaseSort(new Sort("-Created"));
        if (_context.getContainer() == null || _context.getContainer().isRoot())
        {
            view.getViewContext().setPermissions(ACL.PERM_READ);
            view.getRenderContext().setUseContainerFilter(false);
        }
        return view;
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
    {
        if (!_minimal)
        {
            super.populateButtonBar(view, bar, exportAsWebPage);
        }

        if (getContainer().hasPermission(getUser(), InsertPermission.class) && PipelineService.get().hasValidPipelineRoot(getContainer()))
        {
            ActionButton button = new ActionButton("browse.view", "Process and Import Data");
            button.setActionType(ActionButton.Action.LINK);
            button.setURL(PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(getContainer(), PipelineController.RefererValues.pipeline.toString()));
            bar.add(button);
        }

        if (PipelineService.get().canModifyPipelineRoot(getUser(), getContainer()))
        {
            ActionButton button = new ActionButton("setup.view", "Setup");
            button.setActionType(ActionButton.Action.LINK);
            button.setURL(PipelineController.urlSetup(getContainer(), PipelineController.RefererValues.pipeline.toString()));
            bar.add(button);
        }

        if (!_minimal)
        {
            ActionButton retryStatus = new ActionButton("runAction.view?action=" + PipelineProvider.CAPTION_RETRY_BUTTON, PipelineProvider.CAPTION_RETRY_BUTTON);
            retryStatus.setRequiresSelection(true);
            retryStatus.setActionType(ActionButton.Action.POST);
            retryStatus.setDisplayPermission(UpdatePermission.class);
            bar.add(retryStatus);

            ActionButton deleteStatus = new ActionButton("deleteStatus.view", "Delete");
            deleteStatus.setRequiresSelection(true);
            deleteStatus.setActionType(ActionButton.Action.POST);
            deleteStatus.setDisplayPermission(DeletePermission.class);
            bar.add(deleteStatus);

            ActionButton completeStatus = new ActionButton("completeStatus.view", "Complete");
            completeStatus.setRequiresSelection(true);
            completeStatus.setActionType(ActionButton.Action.POST);
            completeStatus.setDisplayPermission(UpdatePermission.class);
            bar.add(completeStatus);

            // Display the "Show Queue" button, if this is not the Enterprise Pipeline,
            // the user is an administrator, and this is the pipeline administration page.
            if (!PipelineService.get().isEnterprisePipeline() &&
                    getUser().isAdministrator() && getContainer().isRoot())
            {
                ActionButton showQueue = new ActionButton((String)null, "Show Queue");
                showQueue.setURL(PipelineController.urlStatus(getContainer(), true));
                bar.add(showQueue);
            }
        }
    }
}
