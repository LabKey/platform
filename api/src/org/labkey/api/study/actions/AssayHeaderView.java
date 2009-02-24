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
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.defaults.SetDefaultValuesAction;
import org.springframework.web.servlet.mvc.Controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

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
            if (allowUpdate(protocol))
            {
                String editLink = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(_protocol.getContainer(), _protocol, false).toString();
                if (!protocol.getContainer().equals(getViewContext().getContainer()))
                {
                    editLink = "javascript: if (window.confirm('This assay is defined in the " + _protocol.getContainer().getPath() + " folder. Would you still like to edit it?')) { window.location = '" + editLink + "' }";
                }
                manageMenu.addChild("edit assay design", editLink);
                ActionURL copyURL = PageFlowUtil.urlProvider(AssayUrls.class).getChooseCopyDestinationURL(_protocol, _protocol.getContainer());
                manageMenu.addChild("copy assay design", copyURL.toString());
            }

            if (allowDelete(protocol))
            {
                ActionURL deleteURL = PageFlowUtil.urlProvider(AssayUrls.class).getDeleteDesignURL(_protocol.getContainer(), _protocol);
                String extraWarning = "";
                if (!protocol.getContainer().equals(getViewContext().getContainer()))
                {
                    extraWarning = " It is defined in " + _protocol.getContainer().getPath() + " and deleting it will remove it from all subfolders.";
                }
                manageMenu.addChild("delete assay design", "javascript: if (window.confirm('Are you sure you want to delete this assay and all of its runs?" + extraWarning + "')) { window.location = '" + deleteURL + "' }");
            }

            ActionURL exportURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getExportProtocolOptionsURL(getViewContext().getContainer(), _protocol);
            manageMenu.addChild("export assay design", exportURL.toString());

            if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), ACL.PERM_ADMIN))
            {
                List<Domain> domains = _provider.getDomains(_protocol);
                if (!domains.isEmpty())
                {
                    NavTree setDefaultsTree = new NavTree("set default values");
                    ActionURL baseEditUrl = new ActionURL(SetDefaultValuesAction.class, getViewContext().getContainer());
                    baseEditUrl.addParameter("returnUrl", getViewContext().getActionURL().getLocalURIString());
                    for (Domain domain : domains)
                    {
                        ActionURL currentEditUrl = baseEditUrl.clone();
                        currentEditUrl.addParameter("domainId", domain.getTypeId());
                        setDefaultsTree.addChild(domain.getName(), currentEditUrl);
                    }
                    manageMenu.addChild(setDefaultsTree);
                }
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

    protected boolean allowUpdate(ExpProtocol protocol)
    {
        ViewContext ctx = getViewContext();
        Container container = protocol.getContainer();
        return container.hasPermission(ctx.getUser(), ACL.PERM_UPDATE) ||
                    (container.hasPermission(ctx.getUser(), ACL.PERM_UPDATEOWN) && ctx.getUser().equals(protocol.getCreatedBy()));
    }

    protected boolean allowDelete(ExpProtocol protocol)
    {
        ViewContext ctx = getViewContext();
        Container container = protocol.getContainer();
        return container.hasPermission(ctx.getUser(), ACL.PERM_DELETE) ||
                    (container.hasPermission(ctx.getUser(), ACL.PERM_DELETEOWN) && ctx.getUser().equals(protocol.getCreatedBy()));
    }
}
