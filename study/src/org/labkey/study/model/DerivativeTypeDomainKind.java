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
 * Created by davebradlee on 2/7/15
 */
public final class DerivativeTypeDomainKind extends AbstractSpecimenDomainKind
{
    private static final String NAME = "SpecimenDerivative";
    private static final String NAMESPACE_PREFIX = "SpecimenDerivative";

    public static final String ROWID = "RowId";
    public static final String CONTAINER = "Container";
    public static final String EXTERNALID = "ExternalId";
    public static final String LDMSDERIVATIVECODE = "LdmsDerivativeCode";
    public static final String LABWAREDERIVATIVECODE = "LabwareDerivativeCode";
    public static final String DERIVATIVE = "Derivative";

    private static final List<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> BASE_INDICES;
    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(ROWID, JdbcType.INTEGER, 0, PropertyStorageSpec.Special.PrimaryKey, false, true, null),
            new PropertyStorageSpec(CONTAINER, JdbcType.GUID, 36, false, null),
            new PropertyStorageSpec(EXTERNALID, JdbcType.INTEGER, 0, false, null),
            new PropertyStorageSpec(LDMSDERIVATIVECODE, JdbcType.VARCHAR, 30),
            new PropertyStorageSpec(LABWAREDERIVATIVECODE, JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(DERIVATIVE, JdbcType.VARCHAR, 100),
        };
        BASE_PROPERTIES = Arrays.asList(props);

        PropertyStorageSpec.Index[] indices =
        {
            new PropertyStorageSpec.Index(true, EXTERNALID),
            new PropertyStorageSpec.Index(false, DERIVATIVE)
        };
        BASE_INDICES = new HashSet<>(Arrays.asList(indices));
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
        return Collections.emptySet();
    }
}
