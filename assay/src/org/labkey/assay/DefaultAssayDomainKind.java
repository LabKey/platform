/*
 * Copyright (c) 2012 LabKey Corporation
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

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.assay.AssayDomainKind;
import org.labkey.api.exp.property.Domain;

import java.util.Set;

/**
 * Catch-all for assay domains that don't have special handlers. Registers itself as low priority so if any other
 * DomainKind matches, it will be used instead.
 * User: jeckels
 * Date: Jan 27, 2012
 */
public class DefaultAssayDomainKind extends AssayDomainKind
{
    public DefaultAssayDomainKind()
    {
        super(ExpProtocol.ASSAY_DOMAIN_PREFIX, Priority.LOW);
    }

    @Override
    public String getKindName()
    {
        return "Assay";
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return getAssayReservedPropertyNames();
    }
}
