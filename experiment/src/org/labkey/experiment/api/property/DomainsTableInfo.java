package org.labkey.experiment.api.property;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.column.BuiltInColumnTypes;

// CONSIDER: Filter to just Vocabulary domains?
public class DomainsTableInfo extends FilteredTable<PropertyUserSchema>
{
    public DomainsTableInfo(PropertyUserSchema schema, ContainerFilter cf)
    {
        super(OntologyManager.getTinfoDomainDescriptor(), schema, cf);
        setName(PropertyUserSchema.TableType.Domains.name());
    }

    public DomainsTableInfo populateColumns()
    {
        TableInfo realTable = getRealTable();

        addWrapColumn("DomainId", realTable.getColumn("DomainId"));
        addWrapColumn("Name", realTable.getColumn("Name"));
        addWrapColumn("DomainURI", realTable.getColumn("DomainURI"));
        addWrapColumn("Description", realTable.getColumn("Description"));
        addWrapColumn("Container", realTable.getColumn("Container"))
            .setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
        addWrapColumn("Modified", realTable.getColumn("Modified"));
        addWrapColumn("ModifiedBy", realTable.getColumn("ModifiedBy"))
            .setConceptURI(BuiltInColumnTypes.USERID_CONCEPT_URI);

        return this;
    }
}
