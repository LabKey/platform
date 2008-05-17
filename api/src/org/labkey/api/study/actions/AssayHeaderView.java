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

package org.labkey.api.study.actions;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.*;
import org.labkey.api.util.PageFlowUtil;

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

    public AssayHeaderView(ExpProtocol protocol, AssayProvider provider, boolean minimizeLinks)
    {
        super("/org/labkey/api/study/actions/assayHeader.jsp");
        setModelBean(this);

        _protocol = protocol;
        _provider = provider;

        NavTree manageMenu = new NavTree();
        if (!minimizeLinks)
        {
            if (_protocol.getContainer().equals(getViewContext().getContainer()))
            {
                if (allowUpdate(protocol))
                {
                    manageMenu.addChild("edit assay design", AssayService.get().getDesignerURL(getViewContext().getContainer(), _protocol, false).toString());
                    ActionURL copyURL = new ActionURL("assay", "chooseCopyDestination",
                            getViewContext().getContainer()).addParameter("rowId", _protocol.getRowId());
                    manageMenu.addChild("copy assay design", copyURL.toString());
                }

                if (allowDelete(protocol))
                {
                    ActionURL deleteURL = new ActionURL("assay", "delete", getViewContext().getContainer());
                    deleteURL.addParameter("rowId", _protocol.getRowId());
                    manageMenu.addChild("delete assay design", "javascript: if (window.confirm('Are you sure you want to delete the assay design and all of its runs?')) { window.location = '" + deleteURL + "' }");
                }

                ActionURL exportURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getExportProtocolOptionsURL(getViewContext().getContainer(), _protocol);
                manageMenu.addChild("export assay design", exportURL.toString());
            }

            if (manageMenu.getChildCount() > 0)
                _managePopupView = new PopupMenuView(manageMenu);

            _links.put("view all runs", AssayService.get().getAssayRunsURL(getViewContext().getContainer(), _protocol));
            _links.put("view all data", AssayService.get().getAssayDataURL(getViewContext().getContainer(), _protocol));

            if (AuditLogService.get().isViewable() && _provider.canPublish())
                _links.put("view copy-to-study history", AssayPublishService.get().getPublishHistory(getViewContext().getContainer(), protocol));
        }
        else
        {
            _links.put("manage", AssayService.get().getAssayRunsURL(getViewContext().getContainer(), protocol));
            _links.put("upload runs", AssayService.get().getUploadWizardURL(getViewContext().getContainer(), protocol));
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
