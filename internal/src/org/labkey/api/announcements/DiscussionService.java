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

package org.labkey.api.announcements;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.Sort;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.*;
import org.labkey.api.util.URLHelper;
import org.labkey.api.action.ReturnUrlForm;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collection;

/**
 * Discussion service is a wrapper for the Announcements controller used as a forum for discussing objects/pages
 *
 * CONSIDER: ideas for future extensions
 *      + understand versioning (e.g. for wiki pages)
 *      + implement a resolver interface for generating links 
 *
 * User: matthewb
 * Date: Feb 1, 2007
 * Time: 2:33:22 PM
 */
public interface DiscussionService
{
    String ACTIVE = "Active";
    String CLOSED = "Closed";

    /* CONSIDER: provide for resolvers rather than (or in addition to) hardcoded url back links
    public interface Resolver
    {
        public ActionURL resolve(Container c, String id, String url)
    }
    registerResolver(String name, Resolver resolver);
    */

    static void register(DiscussionService serviceImpl)
    {
        ServiceRegistry.get().registerService(DiscussionService.class, serviceImpl);
    }

    static DiscussionService get()
    {
        return ServiceRegistry.get(DiscussionService.class);
    }

    /**
     * @param c
     * @param user
     * @param identifier
     * @param pageURL      persistent URL to link back to original page, additional parameter discussionId will automatically be added
     * @param title
     * @param summary
     * @param allowMultipleDiscussions
     * @return WebPartView with a form to start a new discussion, will post directly to Announcements controller
     */
    WebPartView startDiscussion(Container c, User user, String identifier, ActionURL pageURL, URLHelper cancelURL, String title, String summary, boolean allowMultipleDiscussions);

    /** show links, and forms, do it all (wrapper for other methods)
     * @param displayFirstDiscussionByDefault   if true and no discussion parameters are present, display the first
     *                                          discussion associated with this object.
     * @return DiscussionView if EnableDiscussion flag in LookAndFeelProperties is true. If false, null.
     */
    @Nullable
    DiscussionView getDiscussionArea(ViewContext context, String objectId, ActionURL pageURL, String newDiscussionTitle, boolean allowMultipleDiscussions, boolean displayFirstDiscussionByDefault);

    @Nullable
    DiscussionView getDiscussionArea(Container c, User user, URLHelper currentURL, String objectId, ActionURL pageURL, String newDiscussionTitle, boolean allowMultipleDiscussions, boolean displayFirstDiscussionByDefault);

    void deleteDiscussions(Container container, User user, String... identifier);

    void deleteDiscussions(Container container, User user, Collection<String> identifiers);

    boolean hasDiscussions(Container container, String identifier);

    void unlinkDiscussions(Container container, String identifier, User user);

    Settings getSettings(Container container);

    void setSettings(Container container, Settings settings);

    class DiscussionView extends VBox
    {
        private String _focusId;

        public DiscussionView(ModelAndView... views)
        {
            super(views);
        }

        public String getFocusId()
        {
            return _focusId;
        }

        public void setFocusId(String focusId)
        {
            _focusId = focusId;
        }
    }

    class Settings extends ReturnUrlForm
    {
        String _boardName = "Messages";
        String _conversationName = "Message";
        boolean _secure = false;
        boolean _status = false;
        boolean _expires = false;
        boolean _assignedTo = false;
        Integer _defaultAssignedTo = null;
        boolean _formatPicker = false;
        boolean _memberList = false;
        boolean _titleEditable = false;
        boolean _includeGroups = false;
        SortOrder _sortOrder = SortOrder.getDefaultSortOrder();

        String _statusOptions = ACTIVE + ";" + CLOSED;

        public enum SortOrder
        {
            CreationDate(0, "-Created"), LatestResponseDate(1, "-ResponseCreated");

            private int _index;
            private String _sortString;

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

        // Set the defaults that will be used for un-customized message boards.  We must set them to false above to
        // workaround the "checkbox doesn't post if false" problem.
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

        public boolean isSecure()
        {
            return _secure;
        }

        public void setSecure(boolean secure)
        {
            _secure = secure;
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

        public String getStatusOptions()
        {
            return _statusOptions;
        }

        public void setStatusOptions(String options)
        {
            _statusOptions = options;
        }

        public boolean includeGroups()
        {
            return _includeGroups;
        }

        public void setIncludeGroups(boolean includeGroups)
        {
            _includeGroups = includeGroups;
        }
    }
}
