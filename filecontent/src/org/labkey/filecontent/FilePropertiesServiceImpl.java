/*
 * Copyright (c) 2010-2015 LabKey Corporation
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
package org.labkey.filecontent;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.view.ViewContext;
import org.labkey.filecontent.designer.client.FilePropertiesService;

import java.util.List;

/**
 * User: klum
 * Date: Mar 25, 2010
 * Time: 12:50:26 PM
 */
public class FilePropertiesServiceImpl extends DomainEditorServiceBase implements FilePropertiesService
{
    public FilePropertiesServiceImpl(ViewContext context)
    {
        super(context);
    }

    @Override
    public GWTDomain getDomainDescriptor(String typeURI)
    {
        GWTDomain domain = super.getDomainDescriptor(typeURI);
        if (domain != null)
            domain.setDefaultValueOptions(new DefaultValueType[]
                    { DefaultValueType.FIXED_EDITABLE, DefaultValueType.FIXED_NON_EDITABLE }, DefaultValueType.FIXED_EDITABLE);
        return domain;
    }

    @Override
    protected GWTDomain<? extends GWTPropertyDescriptor> getDomainDescriptor(String typeURI, Container domainContainer)
    {
        GWTDomain<? extends GWTPropertyDescriptor> domain = super.getDomainDescriptor(typeURI, domainContainer);
        if (domain != null)
            domain.setDefaultValueOptions(new DefaultValueType[]
                    { DefaultValueType.FIXED_EDITABLE, DefaultValueType.FIXED_NON_EDITABLE }, DefaultValueType.FIXED_EDITABLE);
        return domain;
    }

    /** @return Errors encountered during the save attempt */
    @NotNull
    public List<String> updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update)
    {
        if (orig.getDomainURI() != null)
        {
            DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(orig.getDomainURI(), orig.getName(), getContainer());
            orig.setDomainId(dd.getDomainId());
            orig.setContainer(getContainer().getId());

            return super.updateDomainDescriptor(orig, update);
        }
        else
            throw new IllegalArgumentException("DomainURI cannot be null");
    }
}
