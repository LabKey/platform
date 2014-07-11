/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;

import java.util.Collection;

/**
 * Created by klum on 2/23/14.
 */
public class ProvisionedDbSchema extends DbSchema
{
    public ProvisionedDbSchema(String name, DbSchemaType type, DbScope scope)
    {
        super(name, type, scope, null);
    }

    @Override
    protected <OptionType extends DbScope.SchemaTableOptions> void afterLoadTable(SchemaTableInfo ti, OptionType params)
    {
        super.afterLoadTable(ti, params);
        if (params instanceof StorageProvisioner.ProvisionedSchemaOptions)
        {
            StorageProvisioner.ProvisionedSchemaOptions options = (StorageProvisioner.ProvisionedSchemaOptions)params;
            Domain domain = options.getDomain();
            DomainKind kind = domain.getDomainKind();

            StorageProvisioner.fixupProvisionedDomain(ti, kind, domain, ti.getName());
        }
    }

    @Override
    protected String getMetaDataName(String tableName)
    {
        return tableName;
    }

    @Override
    public Collection<String> getTableNames()
    {
        throw new IllegalStateException("Should not be requesting table names from provisioned schema \"" + getName() + "\"");
    }
}
