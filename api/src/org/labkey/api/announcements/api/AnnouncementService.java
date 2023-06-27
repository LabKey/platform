/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
package org.labkey.api.announcements.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.GUID;

import java.util.List;

/**
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 5:50:52 PM
 */
public interface AnnouncementService
{
    static AnnouncementService get()
    {
        return ServiceRegistry.get().getService(AnnouncementService.class);
    }

    static void setInstance(AnnouncementService impl)
    {
        ServiceRegistry.get().registerService(AnnouncementService.class, impl);
    }

    // IRUD (Insert, Read, Update, Delete)
    Announcement insertAnnouncement(Container container, User u, String title, String body, boolean sendEmailNotification);

    /** @param parentRowId optionally, the existing thread to add the post to. If null, start a new thread */
    Announcement insertAnnouncement(Container container, User u, String title, String body, boolean sendEmailNotification, @Nullable Integer parentRowId);

    /**
     * @param parentRowId optionally, the existing thread to add the post to. If null, start a new thread
     * @param status status for the message thread. Ignored if it does not resolve to a DiscussionService.StatusOption
     * @param memberList List of users that should be added to the notify list; Users that do not have permission to read the message are ignored.
     */
    Announcement insertAnnouncement(Container container, User u, String title, String body, boolean sendEmailNotification, @Nullable Integer parentRowId,
                                    @Nullable String status, @Nullable List<User> memberList);

    // Get One
    Announcement getAnnouncement(Container container, User user, int RowId);

    // Get Many
    List<Announcement> getAnnouncements(Container... containers);

    // Get the latest post
    @Nullable Announcement getLatestPost(Container container, User user, int parentRowId);

    // Update
    Announcement updateAnnouncement(int RowId, Container c, User u, String title, String body);

    // move announcements to the given target container
    int updateContainer(List<String> discussionSrcIds, Container targetContainer, User u);

    // Delete
    void deleteAnnouncement(Announcement announcement);

    @Nullable
    DiscussionSrcTypeProvider getDiscussionSrcTypeProvider(@Nullable String type);

    void registerDiscussionSrcTypeProvider(String type, DiscussionSrcTypeProvider typeProvider);
}
