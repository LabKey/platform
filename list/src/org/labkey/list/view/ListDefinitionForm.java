/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;

public class ListDefinitionForm extends ViewForm
{
    protected ListDefinition _listDef;
    private String[] _deletedAttachments;
    private boolean _showHistory = false;
    private Integer _listId = null;
    private String _name = null;
    private QueryUpdateService.InsertOption _insertOption = QueryUpdateService.InsertOption.IMPORT;

    @NotNull
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
                _listDef = ListService.get().getList(getContainer(), getName(), true);
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
        ReturnUrlForm.throwBadParam();
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

    public QueryUpdateService.InsertOption getInsertOption()
    {
        return _insertOption;
    }

    public void setInsertOption(QueryUpdateService.InsertOption insertOption)
    {
        _insertOption = insertOption;
    }
}
