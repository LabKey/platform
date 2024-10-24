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
package org.labkey.announcements.api;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.announcements.model.Permissions;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.announcements.api.DiscussionSrcTypeProvider;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AnnouncementServiceImpl implements AnnouncementService
{
    private final Map<String, DiscussionSrcTypeProvider> _discussionSrcTypeProviders = new CopyOnWriteHashMap<>();

    @Override
    public Announcement insertAnnouncement(Container c, User u, String title, String body, boolean sendEmailNotification)
    {
        return insertAnnouncement(c, u, title, body, sendEmailNotification, null);
    }

    @Override
    public Announcement insertAnnouncement(Container c, User u, String title, String body, boolean sendEmailNotification, @Nullable Integer parentRowId)
    {
        return insertAnnouncement(c, u, title, body, sendEmailNotification, parentRowId, null, null);
    }

    @Override
    public Announcement insertAnnouncement(Container c, User u, String title, String body, boolean sendEmailNotification, @Nullable Integer parentRowId,
                                           @Nullable String status, @Nullable List<User> memberList)
    {
        DiscussionService.Settings settings = AnnouncementsController.getSettings(c);
        Permissions perm = AnnouncementsController.getPermissions(c, u, settings);

        if (!perm.allowInsert())
        {
            throw new UnauthorizedException();
        }

        AnnouncementModel insert = new AnnouncementModel();
        insert.setTitle(title);
        insert.setBody(body);
        if (status != null && EnumUtils.getEnum(DiscussionService.StatusOption.class, status, null) != null)
        {
            insert.setStatus(status);
        }
        if (CollectionUtils.isNotEmpty(memberList))
        {
            List<Integer> memberListIds = memberList.stream().filter(Objects::nonNull).map(UserPrincipal::getUserId).collect(Collectors.toList());
            AnnouncementModel ann = new AnnouncementModel();
            ann.setMemberListIds(memberListIds);

            List<Integer> validMemberListIds = new ArrayList<>();
            for (User memberUser : memberList)
            {
                if (memberUser != null && AnnouncementManager.getPermissions(c, memberUser, settings).allowRead(ann))
                {
                    // Keep only those users that have the appropriate permission to read the message.
                    validMemberListIds.add(memberUser.getUserId());
                }
            }

            if (validMemberListIds.size() > 0)
            {
                // insert.setMemberListIds(validMemberListIds); // This gets set in AnnouncementManager.validateModelWithSideEffects by parsing the memberListInput
                insert.setMemberListInput(StringUtils.join(validMemberListIds, "\n")); // Pretend this is coming as comma-separated input from a form
            }
        }

        if (parentRowId != null)
        {
            Announcement parentAnnouncement = getAnnouncement(c, u, parentRowId);
            if (parentAnnouncement == null)

            {
                throw new NotFoundException("Can't find a parent announcement with the given id: " + parentRowId);
            }
            insert.setParent(parentAnnouncement.getEntityId());
        }

        List<AttachmentFile> files = Collections.emptyList();

        try
        {
            AnnouncementManager.insertAnnouncement(c, u, insert, files, sendEmailNotification);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return new AnnouncementImpl(insert);
    }

    @Override
    public Announcement getAnnouncement(Container container, User user, int RowId)
    {
        AnnouncementModel model = new AnnouncementModel();
        DiscussionService.Settings settings = AnnouncementsController.getSettings(container);
        Permissions perm = AnnouncementsController.getPermissions(container, user, settings);

        if (RowId != 0)
            model = AnnouncementManager.getAnnouncement(container, RowId);

        if (null == model)
            return null;

        if (!perm.allowRead(model))
            return null;

        return new AnnouncementImpl(model);
    }

    @Override
    public List<Announcement> getAnnouncements(Container... containers)
    {
        List<Announcement> announcements = new ArrayList<>();

        Collection<AnnouncementModel> announcementModels = AnnouncementManager.getAnnouncements(containers); // doesn't allow a filter to be applied

        for (AnnouncementModel announcementModel : announcementModels)
        {
            Announcement announcement = new AnnouncementImpl(announcementModel);
            DiscussionService.Settings settings = AnnouncementsController.getSettings(announcement.getContainer());
            Permissions perm = AnnouncementsController.getPermissions(announcement.getContainer(), HttpView.getRootContext().getUser(), settings);

            if (!perm.allowRead(announcementModel))
                continue; // skip over announcements the user cannot read.
            
            announcements.add(announcement);
        }

        return announcements;
    }

    @Override
    public @Nullable Announcement getLatestPost(Container container, User user, int parentRowId)
    {
        AnnouncementModel parent = AnnouncementManager.getAnnouncement(container, parentRowId);
        if (parent == null)
        {
            return null;
        }
        Integer latestPostId = AnnouncementManager.getLatestPostId(parent);
        return latestPostId == null ? null : getAnnouncement(container, user, latestPostId); // getAnnouncement will do permission checking
    }

    @Override
    public Announcement updateAnnouncement(int RowId, Container c, User u, String title, String body)
    {
        AnnouncementModel model;
        DiscussionService.Settings settings = AnnouncementsController.getSettings(c);
        Permissions perm = AnnouncementsController.getPermissions(c, u, settings);

        model = AnnouncementManager.getAnnouncement(c, RowId);

        if (!perm.allowUpdate(model))
        {
            throw new UnauthorizedException();
        }

        model.setTitle(title);
        model.setBody(body);

        List<AttachmentFile> files = Collections.emptyList();

        try
        {
            AnnouncementManager.updateAnnouncement(u, model, files);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        return new AnnouncementImpl(model);
    }

    @Override
    public int updateContainer(List<String> discussionSrcIds, Container targetContainer, User u)
    {
        return AnnouncementManager.updateContainer(discussionSrcIds, targetContainer, u);
    }

    @Override
    public void deleteAnnouncement(Announcement announcement)
    {
        Container container = announcement.getContainer();
        DiscussionService.Settings settings = AnnouncementsController.getSettings(container);
        Permissions perm = AnnouncementsController.getPermissions(container, HttpView.getRootContext().getUser(), settings);

        if (!perm.allowDeleteAnyThread())
        {
            throw new UnauthorizedException();
        }

        AnnouncementManager.deleteAnnouncement(container, announcement.getRowId());
    }

    @Override
    public @Nullable DiscussionSrcTypeProvider getDiscussionSrcTypeProvider(@Nullable String type)
    {
        if (type == null)
            return null;

        return _discussionSrcTypeProviders.get(type);
    }

    @Override
    public void registerDiscussionSrcTypeProvider(String type, DiscussionSrcTypeProvider typeProvider)
    {
        _discussionSrcTypeProviders.put(type, typeProvider);
    }
}
