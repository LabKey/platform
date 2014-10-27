/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.actions.AssayHeaderView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: jeckels
 * Date: Apr 15, 2009
 */
public class AssayView extends VBox
{
    public void setupViews(QueryView queryView, boolean minimizeLinks, AssayProvider provider, ExpProtocol protocol)
    {
        if (minimizeLinks)
        {
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            queryView.setShowRecordSelectors(false);
        }
        else
        {
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        }

        ModelAndView headerView = createHeaderView(queryView, minimizeLinks, provider, protocol);
        addView(headerView);

        Container container = getViewContext().getContainer();
        if (!PipelineService.get().hasValidPipelineRoot(container))
        {
            StringBuilder html = new StringBuilder();
            html.append("<b>Pipeline root has not been set.</b> ");
            if (container.hasPermission(getViewContext().getUser(), AdminPermission.class))
            {
                ActionURL url = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(container);
                html.append(PageFlowUtil.textLink("setup pipeline", url.getLocalURIString()));
            }
            else
                html.append(" Please ask an administrator for assistance.");
            addView(new HtmlView(html.toString()));
        }

        addView(queryView);
    }

    protected ModelAndView createHeaderView(QueryView queryView, boolean minimizeLinks, AssayProvider provider, ExpProtocol protocol)
    {
        TableInfo tableInfo = queryView.getTable();
        if (tableInfo == null)
        {
            // Not all assay providers support all the different levels of data
            throw new NotFoundException();
        }
        return new AssayHeaderView(protocol, provider, minimizeLinks, true, tableInfo.getContainerFilter());
    }

}
