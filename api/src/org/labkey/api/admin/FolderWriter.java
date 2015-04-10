/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.labkey.api.writer.Writer;
import org.labkey.folder.xml.FolderDocument;

import java.util.Collection;
import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderWriter extends Writer<Container, ImportContext<FolderDocument.Folder>>
{
    @Nullable
    public Collection<Writer> getChildren(boolean sort);
    public boolean show(Container c);
    public boolean selectedByDefault(AbstractFolderContext.ExportType type);
    /* temporary setting until all importers support using VirtualFile */
    boolean supportsVirtualFile();
    void initialize(ImportContext<FolderDocument.Folder> context);
}
