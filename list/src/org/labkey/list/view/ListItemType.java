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
package org.labkey.list.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.list.model.IntegerListDomainKind;
import org.labkey.list.model.ListSchema;
import org.labkey.list.model.PicklistDomainKind;
import org.labkey.list.model.VarcharListDomainKind;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListItemType implements AttachmentParentType
{
    private static final ListItemType INSTANCE = new ListItemType();

    public static ListItemType get()
    {
        return INSTANCE;
    }

    private ListItemType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return "ListItem";
    }

    @Override
    public @NotNull SQLFragment getSelectEntityIdAndDescriptionSql()
    {
        ListService svc = ListService.get();
        assert null != svc;
        SqlDialect dialect = ListSchema.getInstance().getSchema().getSqlDialect();
        List<SQLFragment> selectStatements = new LinkedList<>();

        PropertyService.get().getContainersWithDomains(Set.of(IntegerListDomainKind.NAMESPACE_PREFIX, VarcharListDomainKind.NAMESPACE_PREFIX, PicklistDomainKind.NAMESPACE_PREFIX)).forEach(c -> {
            Map<String, ListDefinition> map = svc.getLists(c, null, false);
            map.forEach((k, v) -> {
                Domain domain = v.getDomain();
                if (null != domain && domain.getProperties().stream().anyMatch(p -> p.getPropertyType() == PropertyType.ATTACHMENT))
                    selectStatements.add(new SQLFragment("\n    SELECT EntityId, ")
                        .append(dialect.concatenate(
                            new SQLFragment("?", domain.getName()),
                            new SQLFragment("':'"),
                            new SQLFragment("CAST(").append(dialect.makeDatabaseIdentifier(v.getKeyName()).getSql())).append(" AS VARCHAR)")
                        )
                        .append(" AS Description FROM list.").append(domain.getStorageTableName())
                    );
            });
        });

        return selectStatements.isEmpty() ?
            NO_ROWS : // No lists with attachment columns
            SQLFragment.join(selectStatements, new SQLFragment("\n    UNION"));
    }
}
