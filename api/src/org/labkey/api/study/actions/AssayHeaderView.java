/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.labkey.api.defaults.SetDefaultValuesAssayAction;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Aug 2, 2007
 */
public class AssayHeaderView extends JspView<AssayHeaderView>
{
    protected final ExpProtocol _protocol;
    protected final AssayProvider _provider;

    protected final boolean _minimizeLinks;
    protected final ContainerFilter _containerFilter;

    public AssayHeaderView(ExpProtocol protocol, AssayProvider provider, boolean minimizeLinks, ContainerFilter containerFilter)
    {
        super("/org/labkey/api/study/actions/assayHeader.jsp");
        _minimizeLinks = minimizeLinks;
        _containerFilter = containerFilter;
        setModelBean(this);

        _protocol = protocol;
        _provider = provider;
    }

    public List<NavTree> getLinks()
    {
        List<NavTree> links = new ArrayList<NavTree>();
        NavTree manageMenu = new NavTree("manage assay design");
        if (!_minimizeLinks)
        {
            if (allowUpdate(_protocol))
            {
                String editLink = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(_protocol.getContainer(), _protocol, false, null).toString();
                if (!_protocol.getContainer().equals(getViewContext().getContainer()))
                {
                    editLink = "javascript: if (window.confirm('This assay is defined in the " + _protocol.getContainer().getPath() + " folder. Would you still like to edit it?')) { window.location = '" + editLink + "' }";
                }
                manageMenu.addChild("edit assay design", editLink);
                ActionURL copyURL = PageFlowUtil.urlProvider(AssayUrls.class).getChooseCopyDestinationURL(_protocol, _protocol.getContainer());
                manageMenu.addChild("copy assay design", copyURL.toString());
            }

            if (allowDelete(_protocol))
            {
                ActionURL deleteURL = PageFlowUtil.urlProvider(AssayUrls.class).getDeleteDesignURL(_protocol.getContainer(), _protocol);
                String extraWarning = "";
                if (!_protocol.getContainer().equals(getViewContext().getContainer()))
                {
                    extraWarning = " It is defined in " + _protocol.getContainer().getPath() + " and deleting it will remove it from all subfolders.";
                }
                manageMenu.addChild("delete assay design", "javascript: if (window.confirm('Are you sure you want to delete this assay and all of its runs?" + extraWarning + "')) { window.location = '" + deleteURL + "' }");
            }

            ActionURL exportURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getExportProtocolURL(_protocol.getContainer(), _protocol);
            manageMenu.addChild("export assay design", exportURL.toString());

            if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), AdminPermission.class))
            {
                List<Pair<Domain, Map<DomainProperty, Object>>> domainInfos = _provider.getDomains(_protocol);
                if (!domainInfos.isEmpty())
                {
                    NavTree setDefaultsTree = new NavTree("set default values");
                    ActionURL baseEditUrl = new ActionURL(SetDefaultValuesAssayAction.class, getViewContext().getContainer());
                    baseEditUrl.addParameter("returnUrl", getViewContext().getActionURL().getLocalURIString());
                    baseEditUrl.addParameter("providerName", _provider.getName());
                    for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : domainInfos)
                    {
                        Domain domain = domainInfo.getKey();
                        if (_provider.allowDefaultValues(domain) && domain.getProperties().length > 0)
                        {
                            ActionURL currentEditUrl = baseEditUrl.clone();
                            currentEditUrl.addParameter("domainId", domain.getTypeId());
                            setDefaultsTree.addChild(domain.getName(), currentEditUrl);
                        }
                    }
                    manageMenu.addChild(setDefaultsTree);
                }
            }

            if (manageMenu.getChildCount() > 0)
                links.add(manageMenu);

            links.add(new NavTree("view batches", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(getViewContext().getContainer(), _protocol, _containerFilter))));
            links.add(new NavTree("view runs", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), _protocol, _containerFilter))));

            if (getProvider().createDataTable(AssayService.get().createSchema(getViewContext().getUser(), getViewContext().getContainer()), _protocol) != null)
            {
                // Not all assay types have results/data
                links.add(new NavTree("view results", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(getViewContext().getContainer(), _protocol, _containerFilter))));
            }

            if (AuditLogService.get().isViewable() && _provider.canCopyToStudy())
                links.add(new NavTree("view copy-to-study history", AssayPublishService.get().getPublishHistory(getViewContext().getContainer(), _protocol, _containerFilter)));
        }
        else
        {
            if (allowUpdate(_protocol))
                links.add(new NavTree("manage", PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), _protocol)));

            if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), InsertPermission.class) && PipelineService.get().hasValidPipelineRoot(getViewContext().getContainer()))
                links.add(new NavTree(AbstractAssayProvider.IMPORT_DATA_LINK_NAME, _provider.getImportURL(getViewContext().getContainer(), _protocol)));
        }
        return links;
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
        return container.getPolicy().hasPermission(ctx.getUser(), DesignAssayPermission.class);
    }

    protected boolean allowDelete(ExpProtocol protocol)
    {
        ViewContext ctx = getViewContext();
        Container container = protocol.getContainer();
        //deleting will delete data as well as design, so user must have both design assay and delete perms
        return container.getPolicy().hasPermissions(ctx.getUser(), DesignAssayPermission.class, DeletePermission.class);
    }
}
