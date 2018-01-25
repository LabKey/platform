/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.list.controllers.ListController;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public class ListServiceImpl implements ListService
{
    public Map<String, ListDefinition> getLists(Container container)
    {
        Map<String, ListDefinition> ret = new CaseInsensitiveHashMap<>();
        for (ListDef def : ListManager.get().getLists(container))
        {
            ListDefinition list = new ListDefinitionImpl(def);
            ret.put(list.getName(), list);
        }
        return ret;
    }

    public boolean hasLists(Container container)
    {
        Collection<ListDef> lists = ListManager.get().getLists(container);
        return !lists.isEmpty();
    }

    public ListDefinition createList(Container container, String name, ListDefinition.KeyType keyType)
    {
        return new ListDefinitionImpl(container, name, keyType, null);
    }

    public ListDefinition createList(Container container, String name, ListDefinition.KeyType keyType, @Nullable TemplateInfo templateInfo)
    {
        return new ListDefinitionImpl(container, name, keyType, templateInfo);
    }

    public ListDefinition getList(Container container, int listId)
    {
        ListDef def = ListManager.get().getList(container, listId);
        return ListDefinitionImpl.of(def);
    }

    @Override
    @Nullable
    public ListDefinition getList(Container container, String name)
    {
        if (name != null)
        {
            for (ListDef def : ListManager.get().getLists(container))
            {
                // DB stores actual name, but can be referenced with different case (#24476)
                if (name.equalsIgnoreCase(def.getName()))
                    return new ListDefinitionImpl(def);
            }
        }
        return null;
    }

    public ListDefinition getList(Domain domain)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("domainid"), domain.getTypeId());
        ListDef def = new TableSelector(ListManager.get().getListMetadataTable(), filter, null).getObject(ListDef.class);
        return ListDefinitionImpl.of(def);
    }

    public ActionURL getManageListsURL(Container container)
    {
        return new ActionURL(ListController.BeginAction.class, container);
    }

    public void importListArchive(InputStream is, BindException errors, Container c, User user) throws Exception
    {
        File dir = FileUtil.createTempDirectory("list");
        ZipUtil.unzipToDirectory(is, dir);

        ListImporter li = new ListImporter();

        List<String> errorList = new LinkedList<>();

        try
        {
            li.processMany(new FileSystemFile(dir), c, user, errorList, Logger.getLogger(ListController.class));

            for (String error : errorList)
                errors.reject(ERROR_MSG, error);
        }
        catch (InvalidFileException e)
        {
            errors.reject(ERROR_MSG, "Invalid list archive");
        }
        catch (ImportException e)
        {
            errors.reject(ERROR_MSG, e.getMessage());
        }
    }

    @Override
    public UserSchema getUserSchema(User user, Container container)
    {
        return new ListQuerySchema(user, container);
    }
}
