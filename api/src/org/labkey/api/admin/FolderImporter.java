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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;


/**
 * Importer for a particular type of data, used during folder import.
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderImporter
{
    String getDataType();

    /** Brief description of the types of objects this class imports */
    String getDescription();

    void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception;

    /**
     * Execute any post processing and return warnings. This is called after process() has been called on all importers.
     * Default is to do nothing and return no warnings.
     */
    default @NotNull Collection<PipelineJobWarning> postProcess(FolderImportContext ctx, VirtualFile root) throws Exception
    {
        return Collections.emptyList();
    }

    /**
     * Map of children data type names to boolean indicating if it is valid for the given import archive context.
     */
    default @Nullable Map<String, Boolean> getChildrenDataTypes(String archiveFilePath, User user, Container container) throws ImportException, IOException
    {
        return null;
    }

    /**
     * Validate if the folder importer is valid for the given import context. Default to true.
     * @return boolean
     */
    default boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
    {
        return true;
    }
}
