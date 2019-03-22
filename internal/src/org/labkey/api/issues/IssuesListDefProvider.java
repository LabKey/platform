/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.issues;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;

/**
 * Created by davebradlee on 8/3/16.
 */
public interface IssuesListDefProvider
{
    String getName();
    String getLabel();
    String getDescription();
    default Domain getDomain()
    {
        DomainKind domainKind = getDomainKind();
        if (null != domainKind)
            return PropertyService.get().getDomain(getDomainContainer(), domainKind.generateDomainURI(IssuesSchema.SCHEMA_NAME, getName(), getDomainContainer(), null));
        return null;
    }

    static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    @Nullable
    DomainKind getDomainKind();

    default boolean isEnabled(Container container)
    {
        return true;
    }
}
