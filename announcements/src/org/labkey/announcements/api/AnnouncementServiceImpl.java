/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.security.User;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Nick
 * Date: Jun 30, 2010
 * Time: 5:39:12 PM
 */
public class AnnouncementServiceImpl implements AnnouncementService.Interface
{
    @Override
    public Announcement insertAnnouncement(Container c, User u, String title, String body)
    {
        AnnouncementModel insert = new AnnouncementModel();
        insert.setTitle(title);
        insert.setBody(body);

        List<AttachmentFile> files = Collections.emptyList();

        try
        {
            AnnouncementManager.insertAnnouncement(c, u, insert, files);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {

        }
        catch (IOException e)
        {

        }
        catch (SQLException e)
        {

        }
        
        AnnouncementImpl announcement = new AnnouncementImpl(insert);
        return announcement;
    }

    @Override
    public Announcement getAnnouncement(Container container, int RowId)
    {
        AnnouncementModel model = new AnnouncementModel();
        try
        {
            model = AnnouncementManager.getAnnouncement(container, RowId);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return new AnnouncementImpl(model);
    }

    @Override
    public List<Announcement> getAnnouncements(Container... containers)
    {
        List<Announcement> announcements = new ArrayList<Announcement>();

        AnnouncementModel[] announcementModels = AnnouncementManager.getAnnouncements(containers);
        
        for (AnnouncementModel announcementModel : announcementModels)
        {
            Announcement announcement = new AnnouncementImpl(announcementModel);
            announcements.add(announcement);
        }

        return announcements;
    }

    @Override
    public Announcement updateAnnouncement(int RowId, Container c, User u, String title, String body)
    {
        AnnouncementModel model = new AnnouncementModel();
        try
        {
            model = AnnouncementManager.getAnnouncement(c, RowId);
            model.setTitle(title);
            model.setBody(body);
            AnnouncementManager.updateAnnouncement(u, model);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return new AnnouncementImpl(model);
    }
    
    @Override
    public void deleteAnnouncement(Announcement announcement)
    {
        try
        {
            AnnouncementManager.deleteAnnouncement(announcement.getContainer(), announcement.getRowId());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
