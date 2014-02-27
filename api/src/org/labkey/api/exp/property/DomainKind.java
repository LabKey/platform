/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.Map;
import java.util.Set;

abstract public class DomainKind implements Handler<String>
{
    abstract public String getKindName();
    abstract public String getTypeLabel(Domain domain);
    abstract public SQLFragment sqlObjectIdsInDomain(Domain domain);

    /**
     * Create a DomainURI for a Domain that may or may not exist yet.
     */
    abstract public String generateDomainURI(String schemaName, String queryName, Container container, User user);

    abstract public ActionURL urlShowData(Domain domain, ContainerUser containerUser);
    abstract public @Nullable ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser);
    abstract public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user);

    abstract public boolean canCreateDefinition(User user, Container container);

    abstract public boolean canEditDefinition(User user, Domain domain);

    // Override to customize the nav trail on shared pages like edit domain
    abstract public void appendNavTrail(NavTree root, Container c, User user);

    // Do any special handling before a PropertyDescriptor is deleted -- do nothing by default
    abstract public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd);

    /**
     * Return the set of names that should not be allowed for properties. E.g.
     * the names of columns from the hard table underlying this type
     * @return set of strings containing the names. This will be compared ignoring case
     */
    abstract public Set<String> getReservedPropertyNames(Domain domain);

    /**
     * Return the set of names that are always required and cannot subsequently
     * be deleted.
     * @return set of strings containing the names. This will be compared ignoring case
     */
    abstract public Set<String> getMandatoryPropertyNames(Domain domain);

    // CONSIDER: have DomainKind supply and IDomainInstance or similiar
    // so that it can hold instance data (e.g. a DatasetDefinition)

    /**
     * Create a Domain appropriate for this DomainKind.
     * @param domain The domain design.
     * @param arguments Any extra arguments.
     * @param container Container
     * @param user User
     * @return The newly created Domain.
     */
    abstract public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user);

    /**
     * Update a Domain definition appropriate for this DomainKind.
     * @param original The original domain definition.
     * @param update The updated domain definition.
     * @param container Container
     * @param user User
     * @return A list of errors collected during the update.
     */
    abstract public List<String> updateDomain(GWTDomain original, GWTDomain update, Container container, User user);

    abstract public Set<PropertyStorageSpec> getBaseProperties();

    /**
     * If domains of this kind should get hard tables automatically provisioned, this returns
     * the db schema where they reside. If it is null, hard tables are not to be provisioned for doamins of this kind.
     */
    abstract public DbScope getScope();
    abstract public String getStorageSchemaName();
    abstract public Set<PropertyStorageSpec.Index> getPropertyIndices();

    /**
     * If domain needs metadata, give the metadat schema and table names
     */
    abstract public String getMetaDataSchemaName();
    abstract public String getMetaDataTableName();

    /**
     * Need to be able to tell if a domain has rows.
     * Perhaps DomainKind should have getTableInfo() method.
     */
    abstract public boolean hasNullValues(Domain domain, DomainProperty prop);

    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Module;
    }

    /** ask the domain to clear caches related to this domain */
    public void invalidate(Domain domain)
    {
        String schemaName = getStorageSchemaName();
        if (null == schemaName)
            return;
        DbSchema schema = getScope().getSchema(schemaName, getSchemaType());
        String storageTableName = domain.getStorageTableName();

        if (null != storageTableName)
            schema.getScope().invalidateTable(schema, storageTableName);
        else
            schema.getScope().invalidateSchema(schemaName, schema.getType());
    }

    /**
     * Set of hard table names in this schema that are not provision tables
     * @return
     */
    abstract public Set<String> getNonProvisionedTableNames();

    abstract public PropertyStorageSpec getPropertySpec(PropertyDescriptor pd, Domain domain);

    /**
     * @return true if we created property descriptors for base properties in the domain
     */
    public boolean hasPropertiesIncludeBaseProperties()
    {
        return false;
    }
}
