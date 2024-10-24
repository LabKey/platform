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

package org.labkey.api.assay;

import org.labkey.api.assay.actions.AssayHeaderView;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * User: brittp
 * Date: Oct 10, 2007
 * Time: 2:24:42 PM
 */
public class AssayRunType extends ExperimentRunType
{
    private final ExpProtocol _protocol;

    public AssayRunType(ExpProtocol protocol, Container c)
    {
        super(protocol.getName(), AssayProtocolSchema.schemaName(AssayService.get().getProvider(protocol), protocol), AssayProtocolSchema.RUNS_TABLE_NAME);
        _protocol = protocol;
    }

    @Override
    public void populateButtonBar(ViewContext context, ButtonBar bar, DataView view, ContainerFilter filter)
    {
        TableInfo table = view.getDataRegion().getTable();
        ActionURL target = PageFlowUtil.urlProvider(AssayUrls.class).getShowSelectedDataURL(context.getContainer(), _protocol);
        if (table.getContainerFilter() != null && table.getContainerFilter().getType() != null)
            target.addParameter("containerFilterName", table.getContainerFilter().getType().name());
        ActionButton viewSelectedButton = new ActionButton(target, "Show Results");
        viewSelectedButton.setURL(target);
        viewSelectedButton.setRequiresSelection(true);
        viewSelectedButton.setActionType(ActionButton.Action.POST);
        bar.add(viewSelectedButton);

        // Hack - this check is to prevent us from showing Link to Study for Illumina or other prototype assays that
        // don't implement any results import yet
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        if (provider != null)
        {
            Domain domain = provider.getResultsDomain(_protocol);
            if (domain != null && domain.getTypeId() > 0)
            {
                StudyUrls urls = PageFlowUtil.urlProvider(StudyUrls.class);

                if (null != urls)
                {
                    ActionURL linkURL = urls.getLinkToStudyURL(context.getContainer(), _protocol);
                    linkURL.addParameter("runIds", true);
                    if (table.getContainerFilter() != null && table.getContainerFilter().getType() != null)
                        linkURL.addParameter("containerFilterName", table.getContainerFilter().getType().name());
                    ActionButton linkSelectedButton = new ActionButton(linkURL, "Link to Study");
                    linkSelectedButton.setDisplayPermission(InsertPermission.class);
                    linkSelectedButton.setURL(linkURL);
                    linkSelectedButton.setRequiresSelection(true);
                    linkSelectedButton.setActionType(ActionButton.Action.POST);
                    bar.add(linkSelectedButton);
                }
            }
        }

        List<ActionButton> buttons = AssayService.get().getImportButtons(_protocol, context.getUser(), context.getContainer(), false);
        bar.addAll(buttons);
    }

    @Override
    public void renderHeader(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        if (provider != null)
        {
            AssayHeaderView header = new AssayHeaderView(_protocol, provider, false, true, ContainerFilter.current(_protocol.getContainer()));
            header.render(request, response);
            response.getWriter().write("<p/>\n");
        }
    }

    @Override
    public Priority getPriority(ExpProtocol protocol)
    {
        if (_protocol.getLSID().equals(protocol.getLSID()))
        {
            return Priority.HIGH;
        }
        return null;
    }
}
