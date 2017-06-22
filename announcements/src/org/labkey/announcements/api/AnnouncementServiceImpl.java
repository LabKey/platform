/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.labkey.announcements.AnnouncementsController;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.announcements.model.Permissions;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.UnauthorizedException;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 5:39:12 PM
 */
public class AnnouncementServiceImpl implements AnnouncementService
{
    @Override
    public Announcement insertAnnouncement(Container c, User u, String title, String body, boolean sendEmailNotification)
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

        List<AttachmentFile> files = Collections.emptyList();

        try
        {
            AnnouncementManager.insertAnnouncement(c, u, insert, files, sendEmailNotification);
        }
        catch (MessagingException | IOException e)
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
}
