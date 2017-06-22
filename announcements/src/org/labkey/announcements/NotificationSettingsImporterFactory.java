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

import org.jetbrains.annotations.NotNull;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.files.FileContentDefaultEmailPref;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.notification.EmailService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.NotificationsType;

import java.util.Collection;
import java.util.Collections;

/**
 * User: cnathe
 * Date: 10/31/12
 */
public class NotificationSettingsImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new NotificationSettingsImporter();
    }

    public class NotificationSettingsImporter implements  FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.NOTIFICATIONS_SETTINGS;
        }

        @Override
        public String getDescription()
        {
            return getDataType().toLowerCase();
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            if (isValidForImportArchive(ctx))
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());
                NotificationsType notifications = ctx.getXml().getNotifications();
                if (notifications.isSetMessagesDefault())
                {
                    int messagesDefault = notifications.getMessagesDefault().getId();
                    MessageConfigService.NotificationOption messagesOption = MessageConfigService.get().getOption(messagesDefault);
                    if (messagesOption != null)
                        AnnouncementManager.saveDefaultEmailOption(ctx.getContainer(), messagesDefault);
                    else
                        ctx.getLogger().error("Unable to find default messages email option for id " + messagesDefault);
                }
                if (notifications.isSetFilesDefault())
                {
                    int filesDefault = notifications.getFilesDefault().getId();
                    MessageConfigService.NotificationOption filesOption = MessageConfigService.get().getOption(filesDefault);
                    if (filesOption != null)
                        EmailService.get().setDefaultEmailPref(ctx.getContainer(), new FileContentDefaultEmailPref(), String.valueOf(filesDefault));
                    else
                        ctx.getLogger().error("Unable to find default files email option for id " + filesDefault);

                }
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isValidForImportArchive(ImportContext<FolderDocument.Folder> ctx) throws ImportException
        {
            return ctx.getXml() != null && ctx.getXml().isSetNotifications();
        }
    }
}