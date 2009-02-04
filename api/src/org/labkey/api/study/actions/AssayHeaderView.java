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

package org.labkey.api.study.actions;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.springframework.web.servlet.mvc.Controller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Aug 2, 2007
 */
public class AssayHeaderView extends JspView<AssayHeaderView>
{
    private final ExpProtocol _protocol;
    private final AssayProvider _provider;
    private PopupMenuView _managePopupView;

    protected Map<String, ActionURL> _links = new LinkedHashMap<String, ActionURL>();

    public AssayHeaderView(ExpProtocol protocol, AssayProvider provider, boolean minimizeLinks, ContainerFilter containerFilter)
    {
        super("/org/labkey/api/study/actions/assayHeader.jsp");
        setModelBean(this);

        _protocol = protocol;
        _provider = provider;

        NavTree manageMenu = new NavTree("manage assay design");
        if (!minimizeLinks)
        {
            if (_protocol.getContainer().equals(getViewContext().getContainer()))
            {
                if (allowUpdate(protocol))
                {
                    manageMenu.addChild("edit assay design", PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(getViewContext().getContainer(), _protocol, false).toString());
                    ActionURL copyURL = PageFlowUtil.urlProvider(AssayUrls.class).getChooseCopyDestinationURL(_protocol, getViewContext().getContainer());
                    manageMenu.addChild("copy assay design", copyURL.toString());
                }

                if (allowDelete(protocol))
                {
                    ActionURL deleteURL = PageFlowUtil.urlProvider(AssayUrls.class).getDeleteDesignURL(getViewContext().getContainer(), _protocol);
                    manageMenu.addChild("delete assay design", "javascript: if (window.confirm('Are you sure you want to delete the assay design and all of its runs?')) { window.location = '" + deleteURL + "' }");
                }

                ActionURL exportURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getExportProtocolOptionsURL(getViewContext().getContainer(), _protocol);
                manageMenu.addChild("export assay design", exportURL.toString());
            }

            if (manageMenu.getChildCount() > 0)
            {
                _managePopupView = new PopupMenuView(manageMenu);
                _managePopupView.setButtonStyle(PopupMenu.ButtonStyle.TEXTBUTTON);
            }

            _links.put("view all batches", PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(getViewContext().getContainer(), _protocol, containerFilter));
            _links.put("view all runs", PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), _protocol, containerFilter));
            _links.put("view all results", PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(getViewContext().getContainer(), _protocol, containerFilter));

            if (AuditLogService.get().isViewable() && _provider.canCopyToStudy())
                _links.put("view copy-to-study history", AssayPublishService.get().getPublishHistory(getViewContext().getContainer(), protocol, containerFilter));
        }
        else
        {
            _links.put("manage", PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), protocol));
            for (Map.Entry<String, Class<? extends Controller>> entry : _provider.getImportActions().entrySet())
            {
                _links.put(entry.getKey().toLowerCase(), PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(getViewContext().getContainer(), _protocol, entry.getValue()));
            }
        }
    }

    public PopupMenuView getManagePopupView()
    {
        return _managePopupView;
    }

    public Map<String, ActionURL> getLinks()
    {
        return _links;
    }

    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    public AssayProvider getProvider()
    {
        return _provider;
    }

    public boolean showProjectAdminLink()
    {
        Container currentContainer = getViewContext().getContainer();
        Container protocolContainer = _protocol.getContainer();
        return (!currentContainer.equals(protocolContainer) &&
                protocolContainer.hasPermission(getViewContext().getUser(), ACL.PERM_ADMIN));
    }

    protected boolean allowUpdate(ExpProtocol protocol)
    {
        ViewContext ctx = getViewContext();
        return ctx.hasPermission(ACL.PERM_UPDATE) ||
                    (ctx.hasPermission(ACL.PERM_UPDATEOWN) && ctx.getUser().equals(protocol.getCreatedBy()));
    }

    protected boolean allowDelete(ExpProtocol protocol)
    {
        ViewContext ctx = getViewContext();
        return ctx.hasPermission(ACL.PERM_DELETE) ||
                    (ctx.hasPermission(ACL.PERM_DELETEOWN) && ctx.getUser().equals(protocol.getCreatedBy()));
    }
}
