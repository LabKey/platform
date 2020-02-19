/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.assay.AssayDomainKind;
import org.labkey.api.exp.TemplateInfo;
import org.json.JSONObject;
import org.labkey.api.exp.api.SampleTypeDomainKindProperties;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.assay.plate.AbstractPlateBasedAssayProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.writer.ContainerUser;
import org.labkey.experiment.api.SampleSetDomainKind;

import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 25, 2012
 */
public class PlateBasedAssaySampleSetDomainKind extends SampleSetDomainKind
{
    AssayDomainKind _assayDelegate;

    public PlateBasedAssaySampleSetDomainKind()
    {
        super();
        _assayDelegate = new AssayDomainKind(AbstractPlateBasedAssayProvider.ASSAY_DOMAIN_SAMPLE_WELLGROUP)
        {
            @Override
            public String getKindName()
            {
                return null;
            }

            @Override
            public Set<String> getReservedPropertyNames(Domain domain)
            {
                return null;
            }
        };
    }

    @Override
    public String getKindName()
    {
        return "Assay Sample Set";
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        if (lsid.getNamespacePrefix().startsWith(AbstractPlateBasedAssayProvider.ASSAY_DOMAIN_SAMPLE_WELLGROUP))
            return Priority.MEDIUM;
        return null;
    }

    /*
     * AssayDomainKind delegating
     */

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return _assayDelegate.urlShowData(domain, containerUser);
    }

    @Override
    public @Nullable ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return _assayDelegate.urlEditDefinition(domain, containerUser);
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return _assayDelegate.canEditDefinition(user, domain);
    }

    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        return _assayDelegate.generateDomainURI(schemaName, queryName, container, user);
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return _assayDelegate.canCreateDefinition(user, container);
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return _assayDelegate.canDeleteDefinition(user, domain);
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return _assayDelegate.urlCreateDefinition(schemaName, queryName, container, user);
    }

    @Override
    public void appendNavTrail(NavTree root, Container c, User user)
    {
        _assayDelegate.appendNavTrail(root, c, user);
    }

    @Override
    public boolean showDefaultValueSettings()
    {
        return true;
    }

    @Override
    public Domain createDomain(GWTDomain domain, SampleTypeDomainKindProperties arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        //TODO this is probably broken.
        return _assayDelegate.createDomain(domain, arguments.toJSONObject(), container, user, templateInfo);
    }

    @NotNull
    @Override
    public ValidationException updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, @Nullable SampleTypeDomainKindProperties options, Container container, User user, boolean includeWarnings)
    {
        //TODO this is probably broken too. Maybe we should throw a not supported exception here?
        JSONObject args = options != null ? options.toJSONObject() : null;
        return _assayDelegate.updateDomain(original, update, args, container, user, includeWarnings);
    }
}
