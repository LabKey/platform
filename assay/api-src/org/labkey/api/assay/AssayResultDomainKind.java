/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

package org.labkey.api.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.dataiterator.SimpleTranslator.SpecialColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.api.assay.AssayFileWriter.DIR_NAME;

public class AssayResultDomainKind extends AssayDomainKind
{
    private static final Set<String> RESERVED_NAMES;
    static
    {
        RESERVED_NAMES = new CaseInsensitiveHashSet(getAssayReservedPropertyNames());
        RESERVED_NAMES.addAll(DomainUtil.getNamesAndLabels(List.of("Run", "DataId")));
    }

    public enum Column
    {
        Plate,
        Replicate,
        ReplicateLsid,
        State,
        WellLocation,
        WellLsid;

        public FieldKey fieldKey()
        {
            return FieldKey.fromParts(name());
        }
    }

    public AssayResultDomainKind()
    {
        super(ExpProtocol.ASSAY_DOMAIN_DATA);
    }

    @Override
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

        PropertyStorageSpec createdSpec = new PropertyStorageSpec(SpecialColumn.Created.name(), JdbcType.TIMESTAMP);
        PropertyStorageSpec createdBySpec = new PropertyStorageSpec(SpecialColumn.CreatedBy.name(), JdbcType.INTEGER);
        PropertyStorageSpec modifiedSpec = new PropertyStorageSpec(SpecialColumn.Modified.name(), JdbcType.TIMESTAMP);
        PropertyStorageSpec modifiedBySpec = new PropertyStorageSpec(SpecialColumn.ModifiedBy.name(), JdbcType.INTEGER);

        return PageFlowUtil.set(rowIdSpec, dataIdSpec, createdSpec, createdBySpec, modifiedSpec, modifiedBySpec);
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return PageFlowUtil.set(new PropertyStorageSpec.Index(false, AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME));
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        return new HashSet<>(Arrays.asList(
            new PropertyStorageSpec.ForeignKey(SpecialColumn.CreatedBy.name(), "core", "users", "userid", null, false),
            new PropertyStorageSpec.ForeignKey(SpecialColumn.ModifiedBy.name(), "core", "users", "userid", null, false)
        ));
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

    public DbSchema getSchema()
    {
        return DbSchema.get(getStorageSchemaName(), getSchemaType());
    }

    @Override
    protected @NotNull Set<String> getKindReservedPropertyNames(Domain domain, User user, boolean forCreate)
    {
        return RESERVED_NAMES;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        Set<String> mandatoryNames = super.getMandatoryPropertyNames(domain);

        Pair<AssayProvider, ExpProtocol> pair = findProviderAndProtocol(domain);
        if (pair != null)
        {
            AssayProvider provider = pair.first;
            ExpProtocol protocol = pair.second;
            if (provider != null && protocol != null)
            {
                if (provider.isPlateMetadataEnabled(protocol))
                {
                    mandatoryNames.add(Column.Plate.name());
                    mandatoryNames.add(Column.WellLocation.name());
                    mandatoryNames.add(Column.WellLsid.name());
                    mandatoryNames.add(Column.ReplicateLsid.name());
                    mandatoryNames.add(Column.State.name());
                }
            }
        }

        return mandatoryNames;
    }

    @Override
    public boolean allowCalculatedFields()
    {
        return true;
    }

    @Override
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
        super.deletePropertyDescriptor(domain, user, pd);

        // SQL Server does not allow for multiple foreign keys to the same table to utilize ON DELETE CASCADE as it may
        // cause cycles or multiple cascade paths. The solution is to only ON DELETE CASCADE for one foreign key and
        // clean up upon delete of the property for other changes. See the "CREATE TABLE assay.FilterCriteria"
        // statement in assay schema upgrade scripts.
        if (!OntologyManager.getSqlDialect().isSqlServer())
            return;

        Pair<AssayProvider, ExpProtocol> pair = findProviderAndProtocol(domain);
        if (pair == null)
            return;

        pair.first.removeFilterCriteriaForProperty(pd);
    }

    @Override
    public String getDomainFileDirectory()
    {
        return DIR_NAME;
    }

    @Override
    public boolean allowMultiChoiceProperties()
    {
        return true;
    }

}
