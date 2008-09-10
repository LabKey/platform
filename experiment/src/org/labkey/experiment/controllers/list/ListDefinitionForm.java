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
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ActionURLException;

import javax.servlet.http.HttpServletRequest;

public class ListDefinitionForm extends ViewForm
{
    protected ListDefinition _listDef;
    private String _returnUrl;
    private String[] _deletedAttachments;
    private boolean _showHistory = false;

    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        super.reset(actionMapping, request);

        // TODO: Use proper validate.  Also, share with ListQueryForm validate
        String listIdParam = request.getParameter("listId");
        if (null == listIdParam)
            throw new NotFoundException("Missing listId parameter");

        try
        {
            _listDef = ListService.get().getList(Integer.parseInt(listIdParam));
        }
        catch (NumberFormatException e)
        {
            throw new NotFoundException("Couldn't convert listId '" + listIdParam + "' to an integer");
        }

        if (null == _listDef)
            throw new NotFoundException("List does not exist");
    }

    public ListDefinition getList()
    {
        return _listDef;
    }

    public String getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(String returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public ActionURL getReturnActionURL()
    {
        try
        {
            return new ActionURL(_returnUrl);
        }
        catch(IllegalArgumentException e)
        {
            throw new ActionURLException(_returnUrl, "returnUrl parameter", e);
        }
        catch(NullPointerException e)
        {
            throw new ActionURLException(_returnUrl, "returnUrl parameter", e);
        }
    }

    public boolean isShowHistory()
    {
        return _showHistory;
    }

    public void setShowHistory(boolean showHistory)
    {
        _showHistory = showHistory;
    }

    public String[] getDeletedAttachments()
    {
        return _deletedAttachments;
    }

    public void setDeletedAttachments(String[] deletedAttachments)
    {
        _deletedAttachments = deletedAttachments;
    }
}
