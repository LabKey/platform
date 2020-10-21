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
package org.labkey.study.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.experiment.api.SampleTypeDomainKind;

public class SpecimenSampleTypeDomainKind extends SampleTypeDomainKind
{
    public SpecimenSampleTypeDomainKind()
    {
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        String prefix = lsid.getNamespacePrefix();
        String name = lsid.getObjectId();
        if ("SampleSet".equals(prefix) && SpecimenService.SAMPLE_TYPE_NAME.equals(name))
            return Priority.HIGH;
        return null;
    }

    @Override
    public String getStorageSchemaName()
    {
        // Don't provision table with no properties
        return null;
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return false;
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return false;
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return false;
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return null;
    }
}
