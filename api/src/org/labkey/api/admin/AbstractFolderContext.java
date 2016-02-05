/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.admin;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public abstract class AbstractFolderContext extends AbstractImportContext<FolderDocument.Folder, FolderDocument>
{
    public enum ExportType
    {
        ALL,
        STUDY
    }

    protected AbstractFolderContext(User user, Container c, FolderDocument folderDoc, Set<String> dataTypes, LoggerGetter logger, @Nullable VirtualFile root)
    {
        super(user, c, folderDoc, dataTypes, logger, root);
    }

    // Folder node -- interesting to any top-level writer that needs to set info into folder.xml
    public FolderDocument.Folder getXml() throws ImportException
    {
        return getDocument().getFolder();
    }
}
