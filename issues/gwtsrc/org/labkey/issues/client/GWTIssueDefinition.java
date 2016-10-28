/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.issues.client;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.util.IntegerProperty;
import org.labkey.api.gwt.client.util.StringProperty;

/**
 * Created by klum on 5/12/2016.
 */
public class GWTIssueDefinition implements IsSerializable
{
    StringProperty _issueDefName = new StringProperty();
    StringProperty _singularItemName = new StringProperty();
    StringProperty _pluralItemName = new StringProperty();
    StringProperty _commentSortDirection = new StringProperty();
    StringProperty _moveToContainer = new StringProperty();
    StringProperty _moveToContainerSelect = new StringProperty();

    StringProperty _assignedToMethod = new StringProperty();
    IntegerProperty _assignedToGroup = new IntegerProperty();
    IntegerProperty _assignedToUser = new IntegerProperty();

    public GWTIssueDefinition()
    {
    }

    public String getSingularItemName()
    {
        return _singularItemName.getString();
    }

    public void setSingularItemName(String singularItemName)
    {
        _singularItemName.set(singularItemName);
    }

    public String getPluralItemName()
    {
        return _pluralItemName.getString();
    }

    public void setPluralItemName(String pluralItemName)
    {
        _pluralItemName.set(pluralItemName);
    }

    public String getCommentSortDirection()
    {
        return _commentSortDirection.getString();
    }

    public void setCommentSortDirection(String commentSortDirection)
    {
        _commentSortDirection.set(commentSortDirection);
    }

    public String getMoveToContainer()
    {
        return _moveToContainer.getString();
    }

    public void setMoveToContainer(String moveToContainer)
    {
        _moveToContainer.set(moveToContainer);
    }

    public String getMoveToContainerSelect()
    {
        return _moveToContainerSelect.getString();
    }

    public void setMoveToContainerSelect(String moveToContainerSelect)
    {
        _moveToContainerSelect.set(moveToContainerSelect);
    }

    public String getAssignedToMethod()
    {
        return _assignedToMethod.getString();
    }

    public void setAssignedToMethod(String assignedToMethod)
    {
        _assignedToMethod.set(assignedToMethod);
    }

    public Integer getAssignedToGroup()
    {
        return _assignedToGroup.getInteger();
    }

    public void setAssignedToGroup(Integer assignedToGroup)
    {
        _assignedToGroup.set(assignedToGroup);
    }

    public Integer getAssignedToUser()
    {
        return _assignedToUser.getInteger();
    }

    public void setAssignedToUser(Integer assignedToUser)
    {
        _assignedToUser.set(assignedToUser);
    }

    public String getIssueDefName()
    {
        return _issueDefName.getString();
    }

    public void setIssueDefName(String issueDefName)
    {
        _issueDefName.set(issueDefName);
    }
}
