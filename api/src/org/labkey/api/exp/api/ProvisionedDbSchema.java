package org.labkey.api.exp.api;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;

import java.util.Map;

/**
 * Created by klum on 2/23/14.
 */
public class ProvisionedDbSchema extends DbSchema
{
    public ProvisionedDbSchema(String name, DbSchemaType type, DbScope scope, Map<String, String> metaDataTableNames)
    {
        super(name, type, scope, metaDataTableNames);
    }

    @Override
    protected <OptionType extends DbScope.SchemaTableOptions> void afterLoadTable(SchemaTableInfo ti, OptionType params)
    {
        if (params instanceof StorageProvisioner.ProvisionedSchemaOptions)
        {
            StorageProvisioner.ProvisionedSchemaOptions options = (StorageProvisioner.ProvisionedSchemaOptions)params;
            Domain domain = options.getDomain();
            DomainKind kind = domain.getDomainKind();

            StorageProvisioner.fixupProvisionedDomain(ti, kind, domain, ti.getName());
        }
    }
}
