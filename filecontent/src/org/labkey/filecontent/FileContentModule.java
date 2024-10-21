/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavService;
import org.labkey.filecontent.message.FileContentDigestProvider;
import org.labkey.filecontent.message.FileEmailConfig;
import org.labkey.filecontent.message.ShortMessageDigest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileContentModule extends DefaultModule
{
    @Override
    public String getName()
    {
        return "FileContent";
    }

    @Override
    public Double getSchemaVersion()
    {
        return 24.000;
    }

    @Override
    protected void init()
    {
        addController("filecontent", FileContentController.class);
        PropertyService.get().registerDomainKind(new FilePropertiesDomainKind());
        FileContentService.setInstance(FileContentServiceImpl.getInstance());
        AttachmentService.get().registerAttachmentType(FileSystemAttachmentType.get());
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new FilesWebPart.Factory()
        );
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        List<String> result = new ArrayList<>();
        FileContentServiceImpl service = FileContentServiceImpl.getInstance();
        Path file = service.getFileRootPath(c, FileContentService.ContentType.files);
        if (file != null && Files.isDirectory(file))
        {
            int fileCount = 0;
            int directoryCount = 0;
            try
            {
                List<String> fileNames = new ArrayList<>();
                List<String> directoryNames = new ArrayList<>();
                try (Stream<Path> paths = Files.list(file))
                {
                    for (Path child : paths.collect(Collectors.toSet()))
                    {
                        if (!Files.isDirectory(child))
                        {
                            fileCount++;
                            if (fileNames.size() < 3)
                            {
                                fileNames.add(FileUtil.getFileName(child));
                            }
                        }
                        else
                        {
                            directoryCount++;
                            if (directoryNames.size() < 3)
                            {
                                directoryNames.add(FileUtil.getFileName(child));
                            }
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
            catch (IOException ignored) {}
        }

        return result;
    }

    @Override
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

        FolderSerializationRegistry fsr = FolderSerializationRegistry.get();
        if (fsr != null)
        {
            fsr.addFactories(new FileWriter.Factory(), new FileImporter.Factory());
        }

        AuditLogService.get().registerAuditType(new FileSystemBatchAuditProvider());

        UsageMetricsService.get().registerUsageMetrics(getName(), () -> {
            Map<String, Object> results = new HashMap<>();

            // During system maintenance, FileRootMaintenanceTask populates FileRootSize and FileRootLastCrawled for every container (subject to a timeout)
            TableInfo containers = CoreSchema.getInstance().getTableInfoContainers();
            String select = "SELECT SUM(FileRootSize) AS TotalSize, MIN(FileRootLastCrawled) EarliestCrawl, COUNT(*) AS AllFileRoots, COUNT(FileRootLastCrawled) AS FileRootsCrawled FROM ";
            Map<String, Object> map = new SqlSelector(containers.getSchema(), select + containers.getSelectName())
                .getMap();
            results.put("fileRootsTotalSize", map.get("TotalSize"));
            results.put("fileRootsEarliestCrawlTime", map.get("EarliestCrawl"));
            long crawled = ((Number)map.get("FileRootsCrawled")).longValue();
            results.put("fileRootsCrawled", crawled);
            results.put("fileRootsNotYetCrawled", ((Number)map.get("AllFileRoots")).longValue() - crawled);

            return results;
        });

        SystemMaintenance.addTask(new FileRootMaintenanceTask());
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
        return PageFlowUtil.set(FileRootManager.FILE_CONTENT_SCHEMA_NAME);
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Set.of(
            FileContentController.TestCase.class,
            FileContentServiceImpl.TestCase.class
        );
    }
}
