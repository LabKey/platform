/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.exp.list;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.io.InputStream;
import java.util.Map;

public interface ListService
{
    static ListService get()
    {
        return ServiceRegistry.get().getService(ListService.class);
    }

    static void setInstance(ListService ls)
    {
        ServiceRegistry.get().registerService(ListService.class, ls);
    }

    Map<String, ListDefinition> getLists(Container container);
    Map<String, ListDefinition> getLists(Container container, @Nullable User user);
    Map<String, ListDefinition> getLists(Container container, @Nullable User user, boolean checkVisibility);
    Map<String, ListDefinition> getLists(Container container, @Nullable User user, boolean checkVisibility, boolean includePicklists, boolean includeProjectAndShared);
    boolean hasLists(Container container);
    boolean hasLists(Container container, boolean includeProjectAndShared);
    ListDefinition createList(Container container, String name, ListDefinition.KeyType keyType);
    ListDefinition createList(Container container, String name, ListDefinition.KeyType keyType, @Nullable TemplateInfo templateInfo, @Nullable ListDefinition.Category category);
    @Nullable ListDefinition getList(Container container, int listId);
    @Nullable ListDefinition getList(Container container, String name);
    @Nullable ListDefinition getList(Container container, String name, boolean includeProjectAndShared);
    ListDefinition getList(Domain domain);
    ActionURL getManageListsURL(Container container);
    UserSchema getUserSchema(User user, Container container);

    /** Picklists can specify different container filtering configurations depending on the container context */
    @Nullable ContainerFilter getPicklistContainerFilter(Container container, User user, @NotNull ListDefinition list);

    void importListArchive(InputStream is, BindException errors, Container c, User user) throws Exception;
}
