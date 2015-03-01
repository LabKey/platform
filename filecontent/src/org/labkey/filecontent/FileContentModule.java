/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavService;
import org.labkey.filecontent.message.FileContentDigestProvider;
import org.labkey.filecontent.message.FileEmailConfig;
import org.labkey.filecontent.message.ShortMessageDigest;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
        return 15.10;
    }

    protected void init()
    {
        addController("filecontent", FileContentController.class);
        PropertyService.get().registerDomainKind(new FilePropertiesDomainKind());
        ServiceRegistry.get().registerService(FileContentService.class, new FileContentServiceImpl());
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
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
        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
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
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public String getName()
            {
                return "Short Message Digest";
            }

            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                ShortMessageDigest.getInstance().initializeTimer();
            }
        });

        // Note: DailyMessageDigest timer is initialized by the AnnouncementModule
        DailyMessageDigest.getInstance().addProvider(new FileContentDigestProvider(FileEmailConfig.DAILY_DIGEST));

        // initialize message config provider
        MessageConfigService.getInstance().registerConfigType(new FileEmailConfig());
        ContainerManager.addContainerListener(new FileContentContainerListener());
        ContainerManager.addContainerListener(FileContentServiceImpl.getInstance());
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
        return Collections.<Class>singleton(FileContentServiceImpl.TestCase.class);
    }
}
