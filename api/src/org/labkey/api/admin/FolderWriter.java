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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AbstractFolderContext.ExportType;
import org.labkey.api.data.Container;
import org.labkey.api.writer.Writer;

import java.util.Collection;
import java.util.Collections;

/**
 * Writer for a particular type of data, used for folder exports.
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderWriter extends Writer<Container, FolderExportContext>
{
    default @NotNull Collection<Writer<?, ?>> getChildren(boolean sort, boolean forTemplate)
    {
        return Collections.emptyList();
    }

    boolean show(Container c);
    boolean selectedByDefault(ExportType type, boolean forTemplate);
    void initialize(FolderExportContext context);
}
