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
