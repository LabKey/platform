/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.list.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.lists.permissions.ManagePicklistsPermission;
import org.labkey.api.security.User;

import java.util.HashSet;
import java.util.Set;

public class PicklistDomainKind extends IntegerListDomainKind
{
    public static final String NAMESPACE_PREFIX = "Picklist";
    protected static final String PICK_OBJECT_COLUMN_NAME = "SampleID";

    @Override
    public String getKindName()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public Set<PropertyStorageSpec> getAdditionalProtectedProperties(Domain domain)
    {
        Set<PropertyStorageSpec> specs = new HashSet<>(super.getAdditionalProtectedProperties(domain));
        specs.add(new PropertyStorageSpec(PICK_OBJECT_COLUMN_NAME, JdbcType.INTEGER));
        return specs;
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission("PicklistDomainKind.canCreateDefinition", user, ManagePicklistsPermission.class);
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        Container c = domain.getContainer();
        return c.hasPermission("ListDomainKind.canEditDefinition for picklist", user, ManagePicklistsPermission.class);
    }
}
