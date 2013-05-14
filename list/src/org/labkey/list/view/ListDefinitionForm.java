/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.URLException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;
import org.labkey.api.util.ReturnURLString;
import org.labkey.list.controllers.ListController;

import java.net.URISyntaxException;

public class ListDefinitionForm extends ViewForm
{
    protected ListDefinition _listDef;
    private ReturnURLString _returnUrl;
    private String[] _deletedAttachments;
    private boolean _showHistory = false;
    private Integer _listId = null;
    private String _name = null;

    public ListDefinition getList()
    {
        if (null == _listDef)
        {
            if (null != getListId())
            {
                _listDef = ListService.get().getList(getContainer(), getListId());
            }
            else if (null != getName())
            {
                _listDef = ListService.get().getList(getContainer(), getName());
            }
            else
            {
                throw new NotFoundException("ListId or Name parameter is required");
            }

            if (null == _listDef)
                throw new NotFoundException("List does not exist in this container");
        }

        return _listDef;
    }

    public ReturnURLString getReturnUrl()
    {
        return _returnUrl;
    }

    // alias to support both returnUrl and srcURL parameters
    public void setSrcURL(ReturnURLString srcUrl)
    {
        _returnUrl = srcUrl;
    }
    
    public void setReturnUrl(ReturnURLString returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public URLHelper getReturnURLHelper()
    {
        try
        {
            if (null == _returnUrl)
                return new ActionURL(ListController.BeginAction.class, getContainer());

            return new URLHelper(_returnUrl);
        }
        catch(URISyntaxException e)
        {
            throw new URLException(_returnUrl.getSource(), "returnUrl parameter", e);
        }
        catch(NullPointerException e)
        {
            throw new URLException(null, "returnUrl parameter", e);
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

    public Integer getListId()
    {
        return _listId;
    }

    public void setListId(Integer listId)
    {
        _listId = listId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }
}
