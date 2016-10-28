/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayHeaderLinkProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

import java.util.List;
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
    protected final boolean _includeDescription;

    public AssayHeaderView(ExpProtocol protocol, AssayProvider provider, boolean minimizeLinks, boolean includeDescription, ContainerFilter containerFilter)
    {
        super("/org/labkey/api/study/actions/assayHeader.jsp");
        _minimizeLinks = minimizeLinks;
        _includeDescription = includeDescription;
        _containerFilter = containerFilter;
        setModelBean(this);

        _protocol = protocol;
        _provider = provider;
    }

    public boolean isIncludeDescription()
    {
        return _includeDescription;
    }

    public List<NavTree> getLinks()
    {
        List<NavTree> links = new ArrayList<>();
        if (!_minimizeLinks)
        {
            links.addAll(getProvider().getHeaderLinks(getViewContext(), _protocol, _containerFilter));
        }
        else
        {
            if (allowUpdate(_protocol))
                links.add(new NavTree("manage", PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), _protocol)));

            if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), InsertPermission.class) && PipelineService.get().hasValidPipelineRoot(getViewContext().getContainer()))
                links.add(new NavTree(AbstractAssayProvider.IMPORT_DATA_LINK_NAME, _provider.getImportURL(getViewContext().getContainer(), _protocol)));
        }

        // give the registered AssayHeaderLinkProviders a chance to include links
        for (AssayHeaderLinkProvider headerLinkProvider : AssayService.get().getAssayHeaderLinkProviders())
        {
            links.addAll(headerLinkProvider.getLinks(_protocol, getViewContext().getContainer(), getViewContext().getUser()));
        }

        return links;
    }

    public static String getDeleteOnClick(ExpProtocol protocol, Container currentContainer)
    {
        ActionURL deleteURL = PageFlowUtil.urlProvider(AssayUrls.class).getDeleteDesignURL(protocol);
        String extraWarning = "";
        if (!protocol.getContainer().equals(currentContainer))
        {
            extraWarning = " It is defined in " + protocol.getContainer().getPath() + " and deleting it will remove it from all subfolders.";
        }
        return "if (window.confirm('Are you sure you want to delete this assay design and all of its runs?" + extraWarning + "')) { window.location = '" + deleteURL + "' }";
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
        return container.hasPermission(ctx.getUser(), DesignAssayPermission.class);
    }

    protected boolean allowDelete(ExpProtocol protocol)
    {
        ViewContext ctx = getViewContext();
        Container container = protocol.getContainer();
        //deleting will delete data as well as design, so user must have both design assay and delete perms
        return container.getPolicy().hasPermissions(ctx.getUser(), DesignAssayPermission.class, DeletePermission.class);
    }
}
