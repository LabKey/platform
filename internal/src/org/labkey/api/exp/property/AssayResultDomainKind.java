/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.exp.property;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.util.PageFlowUtil;

import java.util.Set;

/**
 * User: brittp
 * Date: June 25, 2007
 * Time: 1:01:43 PM
 */
public class AssayResultDomainKind extends AssayDomainKind
{
    public AssayResultDomainKind()
    {
        super(ExpProtocol.ASSAY_DOMAIN_DATA);
    }

    public String getKindName()
    {
        return "Assay Results";
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        PropertyStorageSpec dataIdSpec = new PropertyStorageSpec(AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME, JdbcType.INTEGER);
        dataIdSpec.setNullable(false);

        PropertyStorageSpec rowIdSpec = new PropertyStorageSpec(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME, JdbcType.INTEGER);
        rowIdSpec.setAutoIncrement(true);
        rowIdSpec.setPrimaryKey(true);

        return PageFlowUtil.set(rowIdSpec, dataIdSpec);
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return PageFlowUtil.set(new PropertyStorageSpec.Index(false, AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME));
    }

    @Override
    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return AbstractTsvAssayProvider.ASSAY_SCHEMA_NAME;
    }

    private DbSchema getSchema()
    {
        return DbSchema.get(getStorageSchemaName(), getSchemaType());
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        Set<String> result = getAssayReservedPropertyNames();
        result.add("Run");
        result.add("DataId");
        return result;
    }
}
