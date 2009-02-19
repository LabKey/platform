/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.assay.AssayRunType;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.actions.ShowSelectedDataAction;
import org.labkey.api.study.actions.PublishStartAction;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: brittp
 * Date: Jun 29, 2007
 * Time: 11:13:44 AM
 */
public class RunListQueryView extends ExperimentRunListView
{
    private ExpProtocol _protocol;
    public RunListQueryView(ExpProtocol protocol, UserSchema schema, QuerySettings settings, AssayRunType assayRunFilter)
    {
        super(schema, settings, assayRunFilter);
        _protocol = protocol;
        setShowDeleteButton(true);
        setShowAddToRunGroupButton(false);
    }

    public RunListQueryView(ExpProtocol protocol, ViewContext context)
    {
        this(protocol, getDefaultUserSchema(context),
                getDefaultQuerySettings(protocol, context), getDefaultAssayRunFilter(protocol, context));
    }

    public static AssayRunType getDefaultAssayRunFilter(ExpProtocol protocol, ViewContext context)
    {
        return new AssayRunType(protocol, context.getContainer());
    }

    public static QuerySettings getDefaultQuerySettings(ExpProtocol protocol, ViewContext context)
    {
        UserSchema schema = getDefaultUserSchema(context);
        return ExperimentRunListView.getRunListQuerySettings(schema, context, AssayService.get().getRunsTableName(protocol), true);
    }

    public static UserSchema getDefaultUserSchema(ViewContext context)
    {
        return QueryService.get().getUserSchema(context.getUser(), context.getContainer(), AssayRunType.SCHEMA_NAME);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        ActionURL target = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(getContainer(), _protocol, ShowSelectedDataAction.class);
        if (getTable().getContainerFilter() != null)
            target.addParameter("containerFilterName", getTable().getContainerFilter().getType().name());
        ActionButton viewSelectedButton = new ActionButton(target, "Show Results");
        viewSelectedButton.setURL(target);
        viewSelectedButton.setRequiresSelection(true);
        viewSelectedButton.setActionType(ActionButton.Action.POST);
        bar.add(viewSelectedButton);

        if (AssayService.get().getProvider(_protocol).canCopyToStudy())
        {
            ActionURL copyURL = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(getContainer(), _protocol, PublishStartAction.class);
            copyURL.addParameter("runIds", true);
            if (getTable().getContainerFilter() != null)
                copyURL.addParameter("containerFilterName", getTable().getContainerFilter().getType().name());
            ActionButton copySelectedButton = new ActionButton(copyURL, "Copy to Study");
            copySelectedButton.setURL(copyURL);
            copySelectedButton.setRequiresSelection(true);
            copySelectedButton.setActionType(ActionButton.Action.POST);
            bar.add(copySelectedButton);
        }
    }
}
