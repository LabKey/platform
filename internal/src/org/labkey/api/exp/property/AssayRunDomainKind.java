package org.labkey.api.exp.property;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpRunTable;

import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 27, 2012
 */
public class AssayRunDomainKind extends AssayDomainKind
{
    public AssayRunDomainKind()
    {
        super(ExpProtocol.ASSAY_DOMAIN_RUN);
    }

    public String getKindName()
    {
        return "Assay Runs";
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        Set<String> result = getAssayReservedPropertyNames();
        for (ExpRunTable.Column column : ExpRunTable.Column.values())
        {
            result.add(column.toString());
        }
        result.add("AssayId");
        result.add("Assay Id");
        return result;
    }
}
