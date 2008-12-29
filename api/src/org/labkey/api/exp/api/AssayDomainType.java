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
