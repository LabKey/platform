/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.study.model.AbstractSpecimenDomainKind;
import org.labkey.study.model.SpecimenDomainKind;
import org.labkey.study.model.SpecimenEventDomainKind;
import org.labkey.study.model.VialDomainKind;

public class SpecimenTablesProvider
{
    public static final String SCHEMA_NAME = "specimentables";

    public static final String SPECIMEN_TABLENAME = "Specimen";
    public static final String VIAL_TABLENAME = "Vial";
    public static final String SPECIMENEVENT_TABLENAME = "SpecimenEvent";

    private Container _container;
    private User _user;
    private SpecimenTablesTemplate _template;

    public SpecimenTablesProvider(Container container, @Nullable User user, @Nullable SpecimenTablesTemplate template)
    {
        _container = container;
        _user = user;
        _template = template;
    }

    public static DbSchema getDbSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Provisioned);
    }

    @Nullable
    public final Domain getDomain(String tableName, boolean create)
    {
        // if the domain doesn't exist and we're asked to create, create it
        AbstractSpecimenDomainKind domainKind = getDomainKind(tableName);
        String domainURI = domainKind.generateDomainURI(SCHEMA_NAME, tableName, _container, _user);
        Domain domain = PropertyService.get().getDomain(_container, domainURI);
        if (null == domain && create)
        {
            try
            {
                domain = PropertyService.get().createDomain(_container, domainURI, domainKind.getKindName());

                // Add properties for all required fields
                for (PropertyStorageSpec propSpec : domainKind.getBaseProperties())
                {
                    DomainProperty prop = domain.addProperty(propSpec);
                    prop.setRequired(true);
                }
                if (null != _template)
                {
                    // Add optional fields to table
                    for (PropertyStorageSpec propSpec : domainKind.getPropertySpecsFromTemplate(_template))
                    {
                        domain.addProperty(propSpec);
                    }

                }
                domain.setPropertyForeignKeys(domainKind.getPropertyForeignKeys(_container, this));
                domain.save(_user);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }
        return domain;
    }

    @NotNull
    public TableInfo createTableInfo(String tableName)
    {
        Domain domain = getDomain(tableName, true);
        if (null == domain)
            throw new IllegalStateException("Unable to create domain for table '" + tableName + "'");
        return createTableInfo(domain, _template);
    }

    @Nullable
    public TableInfo getTableInfoIfExists(String tableName)
    {
        Domain domain = getDomain(tableName, false);
        if (null != domain)
            return createTableInfo(domain, null);
        return null;
    }

    public void deleteTables()
    {
        try
        {
            Domain domain = getDomain(SPECIMENEVENT_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
            domain = getDomain(VIAL_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
            domain = getDomain(SPECIMEN_TABLENAME, false);
            if (null != domain)
            {
                domain.delete(_user);
            }
        }
        catch (DomainNotFoundException e)
        {
        }
    }

    // We can cache these within the provider and provider can be cached for container/user (there's only max 1 study per container)
    // but these are not what is registered with Domain stuff, so extra info is only valid to be used here
    private AbstractSpecimenDomainKind _specimenDomainKind = null;
    private AbstractSpecimenDomainKind _vialDomainKind = null;
    private AbstractSpecimenDomainKind _specimenEventDomainKind = null;

    private AbstractSpecimenDomainKind getDomainKind(String tableName)
    {
        if (SPECIMEN_TABLENAME.equalsIgnoreCase(tableName))
        {
            if (null == _specimenDomainKind)
                _specimenDomainKind = new SpecimenDomainKind();
            return _specimenDomainKind;
        }

        if (VIAL_TABLENAME.equalsIgnoreCase(tableName))
        {
            if (null == _vialDomainKind)
            {
                // Vial depends on Specimen
                String specimenDomainURI = getDomainKind(SPECIMEN_TABLENAME).generateDomainURI(SCHEMA_NAME, SPECIMEN_TABLENAME, _container, _user);
                _vialDomainKind = new VialDomainKind(specimenDomainURI);
            }
            return _vialDomainKind;
        }

        if (SPECIMENEVENT_TABLENAME.equalsIgnoreCase(tableName))
        {
            if (null == _specimenEventDomainKind)
            {
                // SpecimenEvent depends on Vial
                String vialDomainURI = getDomainKind(VIAL_TABLENAME).generateDomainURI(SCHEMA_NAME, VIAL_TABLENAME, _container, _user);
                _specimenEventDomainKind =  new SpecimenEventDomainKind(vialDomainURI);
            }
            return _specimenEventDomainKind;
        }

        throw new IllegalStateException("Unknown domain kind: " + tableName);
    }

    @NotNull
    private TableInfo createTableInfo(@NotNull Domain domain, SpecimenTablesTemplate template)
    {
        DomainKind domainKind = domain.getDomainKind();
        return StorageProvisioner.createTableInfo(domain, getDbSchema(), domainKind.getKindName());
    }
}

