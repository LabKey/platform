/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.api.ContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.actions.AssayHeaderView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;

/**
 * User: brittp
 * Date: Aug 21, 2007
 * Time: 9:30:03 AM
 */
public class AssayRunsView extends VBox
{
    public AssayRunsView(ExpProtocol protocol, boolean minimizeLinks, ContainerFilter containerFilter)
    {
        AssayHeaderView headerView = new AssayHeaderView(protocol, AssayService.get().getProvider(protocol), minimizeLinks);
        ViewContext context = getViewContext();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        RunListQueryView runsView = provider.createRunView(context, protocol);
        if (containerFilter != null)
            runsView.setContainerFilter(containerFilter);
        if (minimizeLinks)
        {
            runsView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
        }
        else
        {
            runsView.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        }

        addView(headerView);

        if (!provider.allowUpload(context.getUser(), context.getContainer(), protocol))
            addView(provider.getDisallowedUploadMessageView(context.getUser(), context.getContainer(), protocol));

        addView(runsView);
    }
}
