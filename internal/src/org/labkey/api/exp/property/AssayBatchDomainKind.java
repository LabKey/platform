package org.labkey.api.exp.property;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpExperimentTable;

import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 27, 2012
 */
public class AssayBatchDomainKind extends AssayDomainKind
{
    public AssayBatchDomainKind()
    {
        super(ExpProtocol.ASSAY_DOMAIN_BATCH);
    }

    public String getKindName()
    {
        return "Assay Batches";
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        Set<String> result = super.getAssayReservedPropertyNames();
        for (ExpExperimentTable.Column column : ExpExperimentTable.Column.values())
        {
            result.add(column.toString());
        }
        result.add("AssayId");
        result.add("Assay Id");
        return result;
    }
}
