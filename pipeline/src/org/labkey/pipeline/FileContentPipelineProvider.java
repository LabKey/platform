/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;

/**
 * <code>FileContentPipelineProvider</code>
*/
public class FileContentPipelineProvider extends PipelineProvider
{
    private static Logger _log = Logger.getLogger(FileContentPipelineProvider.class);

    FileContentPipelineProvider()
    {
        super("File Content");
    }

    public void updateFileProperties(ViewContext context, PipeRoot pr, List<FileEntry> entries)
    {
        try
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(context.getContainer());
            SecurityPolicy policy = SecurityManager.getPolicy(root);

            User user = context.getUser();
            //NOTE: the original code tested for ACL.PERM_READ | ACL.PERM_ADMIN, which would require both perms
            //but should this only require ReadPermission? If the user can read, shouldn't the user be able
            //to download the file?
            boolean canRead = policy.hasPermissions(user, ReadPermission.class, AdminPermission.class);
            boolean canDelete = policy.hasPermissions(user, DeletePermission.class, AdminPermission.class);
            boolean canInsert = policy.hasPermissions(user, InsertPermission.class, AdminPermission.class);

            for (FileEntry entry : entries)
            {
                ActionURL urlEntry = entry.cloneHref();
                String path = urlEntry.getParameter("path");
                ActionURL urlDownload = entry.cloneHref().setAction("download");
                ActionURL urlDelete = entry.cloneHref().setAction("delete");

                File[] files = entry.listFiles(new FileEntryFilter() {public boolean accept(File f){return true;}});
                int count=0;
                if (files != null) for (File f : files)
                {
                    if (!canRead || !f.isFile())
                        continue;
                    count++;
                    if (f.canRead())
                    {
                        entry.addAction(new PipelineController.ActionDownloadFile(urlDownload.replaceParameter("path",path + f.getName()), f));
                    }
//                        if (canDelete && f.canWrite())
//                        {
//                            entry.addAction(new ActionDeleteFile(urlDelete.replaceParameter("path",path + f.getName()), f));
//                        }
                }
//                    if (count > 0 && canDelete)
//                    {
//                        FileAction deleteAction = new FileAction("Delete files", "javascript:window.alert('hey');", null);
//                        entry.addAction(deleteAction);
//                    }
//                    if (canInsert)
//                    {
//                        FileAction uploadAction = new FileAction("Upload file", "javascript:window.alert('hey');", null);
//                        entry.addAction(uploadAction);
//                        FileAction createFolderAction = new FileAction("Create folder", "javascript:window.alert('hey');", null);
//                        entry.addAction(createFolderAction);
//                    }
            }
        }
        catch (Exception e)
        {
            _log.error("Exception", e);
        }
    }
}
