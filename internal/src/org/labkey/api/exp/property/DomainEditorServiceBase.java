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

package org.labkey.api.exp.property;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.view.ViewContext;

import java.util.List;

/**
 * User: matthewb
 * Date: May 4, 2007
 * Time: 10:55:27 AM
 * <p/>
 * Base class for building GWT editors that edit domains
 */
public class DomainEditorServiceBase extends BaseRemoteService
{
    public DomainEditorServiceBase(ViewContext context)
    {
        super(context);
    }


    protected void setDefaultValues(GWTDomain domain, String typeURI)
    {
        Domain dom = PropertyService.get().getDomain(getContainer(), typeURI);
        if (dom != null)
        {
            DomainKind kind = dom.getDomainKind();
            domain.setDefaultValueOptions(kind.getDefaultValueOptions(dom), kind.getDefaultDefaultType(dom));
        }
    }

    public GWTDomain getDomainDescriptor(String typeURI)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, getContainer());
    }

    /** @return Errors encountered during the save attempt */
    @NotNull
    public List<String> updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update)
    {
        return DomainUtil.updateDomainDescriptor(orig, update, getContainer(), getUser()).getAllErrors();
    }

    protected GWTDomain<GWTPropertyDescriptor> getDomainDescriptor(String typeURI, Container domainContainer)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, domainContainer);
    }
}
