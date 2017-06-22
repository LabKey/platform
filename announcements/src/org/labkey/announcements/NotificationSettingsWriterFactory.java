/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.announcements;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentDefaultEmailPref;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.notification.EmailService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.NotificationsType;

/**
 * User: cnathe
 * Date: 10/31/12
 */
public class NotificationSettingsWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new NotificationSettingsWriter();
    }

    public class NotificationSettingsWriter extends BaseFolderWriter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.NOTIFICATIONS_SETTINGS;
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            FolderDocument.Folder folderXml = ctx.getXml();

            // add the folder default notification settings for messages
            int messagesDefault = AnnouncementManager.getDefaultEmailOption(c);
            NotificationsType notifications = folderXml.addNewNotifications();
            NotificationsType.MessagesDefault messages = notifications.addNewMessagesDefault();
            messages.setId(messagesDefault);
            MessageConfigService.NotificationOption messagesOption = MessageConfigService.get().getOption(messagesDefault);
            if (messagesOption != null)
            {
                messages.setLabel(messagesOption.getEmailOption());
            }

            /// add the folder default notification settings for file content events
            String pref = EmailService.get().getDefaultEmailPref(c, new FileContentDefaultEmailPref());
            int filesDefault = NumberUtils.toInt(pref);
            NotificationsType.FilesDefault files = notifications.addNewFilesDefault();
            files.setId(filesDefault);
            MessageConfigService.NotificationOption filesOption = MessageConfigService.get().getOption(filesDefault);
            if (filesOption != null)
            {
                files.setLabel(filesOption.getEmailOption());
            }
        }

    }
}
