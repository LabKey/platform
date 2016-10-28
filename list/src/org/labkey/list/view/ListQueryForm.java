/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.list.model.ListQuerySchema;

public class ListQueryForm extends QueryForm
{
    /** Prefer the list referenced by a constructor argument */
    private ListDefinition _primaryDef;
    /**
     * Fall back on those resolved via reflection calls to setters. Useful because when referenced via LABKEY.WebPart
     * JS API, might have a separate "name" parameter on the URL.
     */
    private ListDefinition _reflectionBoundDef;

    public ListQueryForm()
    {
        super(ListQuerySchema.NAME, null);
    }

    public ListQueryForm(int listId, ViewContext context)
    {
        this();
        setViewContext(context);
        _primaryDef = getListDef(listId);
    }

    public ListQueryForm(String listName, ViewContext context)
    {
        this();
        setViewContext(context);
        _primaryDef = ListService.get().getList(getContainer(), listName);
    }

    // Set by spring binding reflection
    @SuppressWarnings({"UnusedDeclaration"})
    public void setListId(int listId)
    {
        _reflectionBoundDef = getListDef(listId);
    }

    // Set by spring binding reflection
    @SuppressWarnings({"UnusedDeclaration"})
    public void setName(String name)
    {
        _reflectionBoundDef = ListService.get().getList(getContainer(), name);
    }

    private ListDefinition getListDef(int listId)
    {
        return ListService.get().getList(getContainer(), listId);
    }

    protected QuerySettings createQuerySettings(UserSchema schema)
    {
        QuerySettings ret = super.createQuerySettings(schema);
        ListDefinition list = getList();
        if (list != null)
        {
            ret.setQueryName(list.getName());
        }
        return ret;
    }

    public ListDefinition getList()
    {
        return _primaryDef == null ? _reflectionBoundDef : _primaryDef;
    }
}
