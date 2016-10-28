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
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;
import org.labkey.list.controllers.ListController;

public class ListDefinitionForm extends ViewForm
{
    protected ListDefinition _listDef;
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

    // alias to support both returnUrl and srcURL parameters
    public void setSrcURL(String srcUrl)
    {
        setReturnUrl(srcUrl);
    }
    
    public URLHelper getDefaultReturnURLHelper()
    {
            return new ActionURL(ListController.BeginAction.class, getContainer());
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
