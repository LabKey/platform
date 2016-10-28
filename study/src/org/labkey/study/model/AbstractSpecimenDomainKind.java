/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.study.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.data.xml.TableType;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractSpecimenDomainKind extends AbstractDomainKind
{
    abstract protected String getNamespacePrefix();
    abstract public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(@Nullable SpecimenTablesTemplate template);
    public AbstractSpecimenDomainKind()
    {
        super();
    }

    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        Lsid lsid = new Lsid(getNamespacePrefix(), container.getId(), schemaName.toLowerCase() + "-" + queryName.toLowerCase());
        return lsid.toString();
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return new SQLFragment("NULL");
    }

    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return new ActionURL(StudyController.ManageStudyAction.class, containerUser.getContainer());   // TODO: view specimen grid
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix() != null && getNamespacePrefix().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return new HashSet<>();
    }

    @Override
    public DbScope getScope()
    {
        return StudySchema.getInstance().getSchema().getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return SpecimenTablesProvider.SCHEMA_NAME;
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public String getMetaDataSchemaName()
    {
        return "study";
    }

    @Override
    public String getMetaDataTableName()
    {
        return getKindName();
    }

    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container, SpecimenTablesProvider provider)
    {
        return Collections.emptySet();
    }

    protected void setForeignKeyTableInfos(Container container, Set<PropertyStorageSpec.ForeignKey> foreignKeys, SpecimenTablesProvider provider)
    {
        // If this table requires FK to other provisioned tables (must be in same dbschema), get those tables
        for (PropertyStorageSpec.ForeignKey foreignKey : foreignKeys)
        {
            if (foreignKey.isProvisioned())
            {
                Domain domain = provider.getDomain(foreignKey.getTableName(), true);
                if (null == domain)
                    throw new IllegalStateException("Expected domain to be created if it didn't already exist.");

                TableInfo tableInfo = StorageProvisioner.createTableInfo(domain);
                foreignKey.setTableInfoProvisioned(tableInfo);
            }
        }
    }

    @Override
    public void afterLoadTable(SchemaTableInfo ti, Domain domain)
    {
        // Grab the meta data for this table (event, vial, or specimen) and apply it to the provisioned table
        DbSchema studySchema = StudySchema.getInstance().getSchema();
        TableType xmlTable = studySchema.getTableXmlMap().get(getMetaDataTableName());
        ti.loadTablePropertiesFromXml(xmlTable, true);
    }
}
