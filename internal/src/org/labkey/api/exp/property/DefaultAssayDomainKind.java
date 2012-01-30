package org.labkey.api.exp.property;

import org.labkey.api.exp.api.ExpProtocol;

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
