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
package org.labkey.api.exp.property;

import org.labkey.api.exp.Lsid;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;

import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 25, 2012
 */
public class PlateBasedAssaySampleSetDomainKind extends AssayDomainKind
{
    public PlateBasedAssaySampleSetDomainKind()
    {
        super(AbstractPlateBasedAssayProvider.ASSAY_DOMAIN_SAMPLE_WELLGROUP);
    }

    public String getKindName()
    {
        return "Assay Sample Set";
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return getAssayReservedPropertyNames();
    }
}
