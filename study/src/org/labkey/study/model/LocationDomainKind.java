/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by davebradlee on 2/6/15
 */
public final class LocationDomainKind extends AbstractSpecimenDomainKind
{
    private static final String NAME = "Location";
    private static final String NAMESPACE_PREFIX = "Location";
    private static final String METADATA_NAME = "Site";

    private static final String ROWID = "RowId";
    private static final String LABEL = "Label";
    private static final String ENTITYID = "EntityId";
    private static final String CONTAINER = "Container";
    private static final String EXTERNALID = "ExternalId";
    private static final String LDMSLABCODE = "LdmsLabCode";
    private static final String LABWARELABCODE = "LabwareLabCode";
    private static final String LABUPLOADCODE = "LabUploadCode";
    private static final String REPOSITORY = "Repository";
    private static final String CLINIC = "Clinic";
    private static final String SAL = "SAL";
    private static final String ENDPOINT = "Endpoint";
    private static final String DESCRIPTION = "Description";
    private static final String STREETADDRESS = "StreetAddress";
    private static final String CITY = "City";
    private static final String GOVERNINGDISTRICT = "GoverningDistrict";
    private static final String COUNTRY = "Country";
    private static final String POSTALAREA = "PostalArea";
    private static final String INUSE = "InUse";        // Is location InUse, not counting SpecimenRequest and related tables

    private static final List<PropertyStorageSpec> BASE_PROPERTIES;
    private static final List<PropertyStorageSpec> ADDED_PROPERTIES;    // Properties needed to upgrade table
    private static final Set<PropertyStorageSpec.Index> BASE_INDICES;
    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(ROWID, JdbcType.INTEGER, 0, PropertyStorageSpec.Special.PrimaryKey, false, true, null),
            new PropertyStorageSpec(LABEL, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(ENTITYID, JdbcType.GUID, 36, false, null),
            new PropertyStorageSpec(CONTAINER, JdbcType.GUID, 36, false, null),
            new PropertyStorageSpec(EXTERNALID, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(LDMSLABCODE, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(LABWARELABCODE, JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(LABUPLOADCODE, JdbcType.VARCHAR, 10),
            new PropertyStorageSpec(REPOSITORY, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(CLINIC, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(SAL, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(ENDPOINT, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(DESCRIPTION, JdbcType.VARCHAR, 500),
            new PropertyStorageSpec(STREETADDRESS, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(CITY, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(GOVERNINGDISTRICT, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(COUNTRY, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(POSTALAREA, JdbcType.VARCHAR, 50)
        };
        BASE_PROPERTIES = Arrays.asList(props);
        BASE_INDICES = Collections.emptySet();

        PropertyStorageSpec[] addedProps = {new PropertyStorageSpec(INUSE, JdbcType.BOOLEAN, 0, true, false)};
        ADDED_PROPERTIES = Arrays.asList(addedProps);
    }

    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return new LinkedHashSet<>(BASE_PROPERTIES);
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return new HashSet<>(BASE_INDICES);
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container, SpecimenTablesProvider provider)
    {
        return Collections.emptySet();
    }

    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(@Nullable SpecimenTablesTemplate template)
    {
        return getAddedProperties();
    }

    @Override
    public String getMetaDataTableName()
    {
        return METADATA_NAME;
    }

    public Set<PropertyStorageSpec> getAddedProperties()
    {
        return new LinkedHashSet<>(ADDED_PROPERTIES);
    }
}
