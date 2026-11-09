/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.announcements.model;

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.Sort;

public class Settings extends ReturnUrlForm
{
    public static final String SECURE_OFF = "secureOff";
    public static final String SECURE_WITHOUT_EMAIL = "secureWithoutEmail";
    public static final String SECURE_WITH_EMAIL = "secureWithEmail";

    String _boardName = "Messages";
    String _conversationName = "Message";
    String _secure = SECURE_OFF;
    boolean _status = false;
    boolean _expires = false;
    boolean _assignedTo = false;
    Integer _defaultAssignedTo = null;
    boolean _formatPicker = false;
    boolean _memberList = false;
    boolean _titleEditable = false;
    boolean _includeGroups = false;
    SortOrder _sortOrder = SortOrder.getDefaultSortOrder();
    String _moderatorReview = "None";

    public enum SortOrder
    {
        CreationDate(0, "-Created"), LatestResponseDate(1, "-ResponseCreated");

        private final int _index;
        private final String _sortString;

        SortOrder(int index, String sortString)
        {
            _index = index;
            _sortString = sortString;
        }

        public int getIndex()
        {
            return _index;
        }

        public Sort getSort()
        {
            return new Sort(_sortString);
        }

        public static SortOrder getByIndex(int index)
        {
            for (SortOrder so : values())
            {
                if (index == so.getIndex())
                    return so;
            }
            return getDefaultSortOrder();  // Bad index -- just return default
        }

        public static SortOrder getDefaultSortOrder()
        {
            return CreationDate;
        }


        // For convenience, used in customize.jsp
        @Override
        public String toString()
        {
            return String.valueOf(_index);
        }
    }

    // Set the defaults that will be used for un-customized message boards. We must set them to false above to
    // work around the "checkbox doesn't post if false" problem.
    public void setDefaults()
    {
        _formatPicker = true;
        _titleEditable = true;
    }

    public String getBoardName()
    {
        return _boardName;
    }

    public void setBoardName(String boardName)
    {
        _boardName = boardName;
    }

    public String getConversationName()
    {
        return _conversationName;
    }

    public void setConversationName(String itemName)
    {
        _conversationName = itemName;
    }

    public String getSecure()
    {
        return _secure;
    }

    public void setSecure(String secure)
    {
        _secure = secure;
    }

    public boolean isSecureOff()
    {
        return (SECURE_OFF).equals(_secure);
    }

    public boolean isSecureOn()
    {
        return (SECURE_WITHOUT_EMAIL).equals(_secure) || (SECURE_WITH_EMAIL).equals(_secure);
    }

    public boolean isSecureWithoutEmailOn()
    {
        return (SECURE_WITHOUT_EMAIL).equals(_secure);
    }

    public boolean isSecureWithEmailOn()
    {
        return (SECURE_WITH_EMAIL).equals(_secure);
    }

    public boolean hasExpires()
    {
        return _expires;
    }

    public void setExpires(boolean expires)
    {
        _expires = expires;
    }

    public boolean hasFormatPicker()
    {
        return _formatPicker;
    }

    public void setFormatPicker(boolean formatPicker)
    {
        _formatPicker = formatPicker;
    }

    public boolean hasAssignedTo()
    {
        return _assignedTo;
    }

    public void setAssignedTo(boolean assignedTo)
    {
        _assignedTo = assignedTo;
    }

    public Integer getDefaultAssignedTo()
    {
        return _defaultAssignedTo;
    }

    public void setDefaultAssignedTo(Integer defaultAssignedTo)
    {
        _defaultAssignedTo = defaultAssignedTo;
    }

    public boolean hasStatus()
    {
        return _status;
    }

    public void setStatus(boolean status)
    {
        _status = status;
    }

    public boolean hasMemberList()
    {
        return _memberList;
    }

    public void setMemberList(boolean memberList)
    {
        _memberList = memberList;
    }

    // Keep this for backward compatibility with message boards that saved a "userList" setting.  These settings are loaded by reflection.
    @Deprecated
    public boolean hasUserList()
    {
        return hasMemberList();
    }

    // Keep this for backward compatibility with message boards that saved a "userList" setting.  These settings are loaded by reflection.
    @Deprecated
    public void setUserList(boolean memberList)
    {
        setMemberList(memberList);
    }

    public int getSortOrderIndex()
    {
        return _sortOrder.getIndex();
    }

    public void setSortOrderIndex(int index)
    {
        _sortOrder = SortOrder.getByIndex(index);
    }

    public Sort getSort()
    {
        return _sortOrder.getSort();
    }

    public boolean isTitleEditable()
    {
        return _titleEditable;
    }

    public void setTitleEditable(boolean titleEditable)
    {
        _titleEditable = titleEditable;
    }

    public boolean includeGroups()
    {
        return _includeGroups;
    }

    public void setIncludeGroups(boolean includeGroups)
    {
        _includeGroups = includeGroups;
    }

    public String getModeratorReview()
    {
        return _moderatorReview;
    }

    public void setModeratorReview(String moderatorReview)
    {
        _moderatorReview = moderatorReview;
    }
}
