package org.labkey.api.exp.property;

import org.labkey.api.exp.Lsid;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;

/**
 * User: jeckels
 * Date: Jan 25, 2012
 */
public class PlateBasedAssaySampleSetDomainKind extends AssayDomainKind
{
    public String getKindName()
    {
        return "Assay Sample Set";
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(AbstractPlateBasedAssayProvider.ASSAY_DOMAIN_SAMPLE_WELLGROUP) ? Priority.HIGH : null;
    }
}
