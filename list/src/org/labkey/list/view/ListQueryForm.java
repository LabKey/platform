/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.labkey.list.model.ListSchema;

public class ListQueryForm extends QueryForm
{
    private ListDefinition _def;
    private boolean _exportAsWebPage = false;

    public ListQueryForm()
    {
        super(ListSchema.NAME, null);
    }

    public ListQueryForm(int listId, ViewContext context)
    {
        this();
        setViewContext(context);
        _def = getListDef(listId);
    }

    public ListQueryForm(String listName, ViewContext context)
    {
        this();
        setViewContext(context);
        setName(listName);
    }

    // Set by spring binding reflection
    @SuppressWarnings({"UnusedDeclaration"})
    public void setListId(int listId)
    {
        _def = getListDef(listId);
    }

    // Set by spring binding reflection
    @SuppressWarnings({"UnusedDeclaration"})
    public void setName(String name)
    {
        _def = ListService.get().getList(getContainer(), name);
    }

    private ListDefinition getListDef(int listId)
    {
        return ListService.get().getList(getContainer(), listId);
    }

    protected QuerySettings createQuerySettings(UserSchema schema)
    {
        QuerySettings ret = super.createQuerySettings(schema);
        ret.setQueryName(_def.getName());
        return ret;
    }

    public ListDefinition getList()
    {
        return _def;
    }

    public boolean isExportAsWebPage()
    {
        return _exportAsWebPage;
    }

    public void setExportAsWebPage(boolean exportAsWebPage)
    {
        _exportAsWebPage = exportAsWebPage;
    }
}
