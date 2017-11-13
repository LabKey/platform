/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.filecontent;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavService;
import org.labkey.filecontent.message.FileContentDigestProvider;
import org.labkey.filecontent.message.FileEmailConfig;
import org.labkey.filecontent.message.ShortMessageDigest;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class FileContentModule extends DefaultModule
{
    public String getName()
    {
        return "FileContent";
    }

    public double getVersion()
    {
        return 17.30;
    }

    protected void init()
    {
        addController("filecontent", FileContentController.class);
        PropertyService.get().registerDomainKind(new FilePropertiesDomainKind());
        FileContentService.setInstance(FileContentServiceImpl.getInstance());
        AttachmentService.get().registerAttachmentType(FileSystemAttachmentType.get());
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(
                new FilesWebPart.Factory()
        ));
    }

    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        List<String> result = new ArrayList<>();
        FileContentService service = FileContentService.get();
        File file = service.getFileRoot(c, FileContentService.ContentType.files);
        if (file != null && NetworkDrive.exists(file) && file.isDirectory())
        {
            int fileCount = 0;
            int directoryCount = 0;
            File[] children = file.listFiles();
            if (children != null)
            {
                List<String> fileNames = new ArrayList<>();
                List<String> directoryNames = new ArrayList<>();
                for (File child : children)
                {
                    if (child.isFile())
                    {
                        fileCount++;
                        if (fileNames.size() < 3)
                        {
                            fileNames.add(child.getName());
                        }
                    }
                    else
                    {
                        directoryCount++;
                        if (directoryNames.size() < 3)
                        {
                            directoryNames.add(child.getName());
                        }
                    }
                }
                if (fileCount == 1)
                {
                    result.add("One file: " + fileNames.get(0));
                }
                if (fileCount > 1)
                {
                    result.add(fileCount + " files, including: " + StringUtils.join(fileNames, ", "));
                }
                if (directoryCount == 1)
                {
                    result.add("One directory in the file system, which may contain additional files: " + directoryNames.get(0));
                }
                if (directoryCount > 1)
                {
                    result.add(directoryCount + " directories in the file system, which may contain additional files, including: " + StringUtils.join(directoryNames, ", "));
                }
            }
        }

        return result;
    }


    public void doStartup(ModuleContext moduleContext)
    {
        WebdavService.get().addProvider(new FileWebdavProvider());

        // initialize message digests
        ShortMessageDigest.getInstance().addProvider(new FileContentDigestProvider(FileEmailConfig.SHORT_DIGEST));

        // Note: DailyMessageDigest timer is initialized by the AnnouncementModule
        DailyMessageDigest.getInstance().addProvider(new FileContentDigestProvider(FileEmailConfig.DAILY_DIGEST));

        // initialize message config provider
        MessageConfigService.get().registerConfigType(new FileEmailConfig());
        ContainerManager.addContainerListener(new FileContentContainerListener());
        ContainerManager.addContainerListener(FileContentServiceImpl.getInstance().getContainerListener());

        FolderSerializationRegistry fsr = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (fsr != null)
        {
            fsr.addFactories(new FileWriter.Factory(), new FileImporter.Factory());
        }

        // populate File Site Root Settings with values read from startup properties as appropriate for not bootstrap
        FileContentServiceImpl.populateSiteRootFileWithStartupProps();
    }

    @Override
    public void startBackgroundThreads()
    {
        ShortMessageDigest.getInstance().initializeTimer();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(FileRootManager.FILECONTENT_SCHEMA_NAME);
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<>(Arrays.asList(
            FileContentServiceImpl.TestCase.class,
            FileContentController.TestCase.class
        ));
    }
}
