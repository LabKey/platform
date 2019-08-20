/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.labkey.api.study.assay.AbstractAssayProvider;

/**
 * User: kevink
 * Date: Dec 23, 2008
 */
public class AssayDomainType implements IAssayDomainType
{
    private String name;
    private String prefix;

    AssayDomainType(String name)
    {
        this.name = name;
        this.prefix = ExpProtocol.ASSAY_DOMAIN_PREFIX + getName();
    }

    public String getName()
    {
        return this.name;
    }

    public String getPrefix()
    {
        return this.prefix;
    }

    public String getLsidTemplate()
    {
        return AbstractAssayProvider.getPresubstitutionLsid(getPrefix());
    }
}
