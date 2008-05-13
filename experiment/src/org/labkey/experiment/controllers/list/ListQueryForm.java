/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.controllers.list;

import org.apache.struts.action.ActionMapping;
import org.labkey.api.data.Container;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.experiment.list.ListSchema;

import javax.servlet.http.HttpServletRequest;

public class ListQueryForm extends QueryForm
{
    ListDefinition _def;
    boolean _exportAsWebPage = false;

    public ListQueryForm()
    {
    }

    public ListQueryForm(int listId, User user, Container c)
    {
        setUser(user);
        setContainer(c);
        _def = getListDef(listId);
    }

    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        super.reset(actionMapping, request);
        _def = getListDef(request.getParameter("listId"));
    }

    private ListDefinition getListDef(int listId)
    {
        ListDefinition listDef = ListService.get().getList(listId);

        if (null == listDef)
            throw new NotFoundException("List does not exist");

        return listDef;
    }

    // TODO: Move to Spring, use proper validate.  Also, share with ListDefinitionForm validate
    private ListDefinition getListDef(String listIdParam)
    {
        if (null == listIdParam)
            throw new NotFoundException("Missing listId parameter");

        int listId;

        try
        {
            listId = Integer.parseInt(listIdParam);
        }
        catch (NumberFormatException e)
        {
            throw new NotFoundException("Couldn't convert listId '" + listIdParam + "' to an integer");
        }

        return getListDef(listId);
    }

    protected UserSchema createSchema()
    {
        return new ListSchema(getUser(), getContainer());
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
