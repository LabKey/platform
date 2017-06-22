/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.writer.ContainerUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 1/30/2017
 *
 * For the StorageProvisioner integration tests, when the Study module is not present.
 *
 */
public class TestDomainKind extends DomainKind
{
    public static final String NAME = "TestDomainKind";
    private static final String SCHEMA = DbSchema.TEMP_SCHEMA_NAME;
    private static final Set<PropertyStorageSpec> _baseFields;
    private static final Set<PropertyStorageSpec.Index> _propertyIndices;

    static
    {
        Set<PropertyStorageSpec> baseFields = new LinkedHashSet<>();
        baseFields.add(new PropertyStorageSpec("RowId", JdbcType.INTEGER, 0, PropertyStorageSpec.Special.PrimaryKey, false, true, null));       // pk
        baseFields.add(new PropertyStorageSpec("Container", JdbcType.VARCHAR).setEntityId(true));
        baseFields.add(new PropertyStorageSpec("Comment", JdbcType.VARCHAR));
        baseFields.add(new PropertyStorageSpec("Created", JdbcType.TIMESTAMP));
        baseFields.add(new PropertyStorageSpec("CreatedBy", JdbcType.INTEGER));
        _baseFields = Collections.unmodifiableSet(baseFields);
    }

    static
    {
        Set<PropertyStorageSpec.Index> propIndices = new LinkedHashSet<>();
        propIndices.add(new PropertyStorageSpec.Index(false, "Container"));
        _propertyIndices = Collections.unmodifiableSet(propIndices);
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void appendNavTrail(NavTree root, Container c, User user)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
        // no op
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, Container container, User user)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(@Nullable Domain domain)
    {
        return _baseFields;
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public DbScope getScope()
    {
        return DbSchema.get(SCHEMA, DbSchemaType.Provisioned).getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return SCHEMA;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return _propertyIndices;
    }

    @Override
    public String getMetaDataSchemaName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getMetaDataTableName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNullValues(Domain domain, DomainProperty prop)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getNonProvisionedTableNames()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("testtable", "testtable2")));
    }

    @Override
    public PropertyStorageSpec getPropertySpec(PropertyDescriptor pd, Domain domain)
    {
        return new PropertyStorageSpec(pd);
    }

    @Nullable
    @Override
    public Priority getPriority(String object)
    {
        return object.contains(NAME) ? Priority.MEDIUM : null;
    }
}
