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

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;


/**
 * Importer for a particular type of data, used during folder import.
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderImporter<DocumentRoot extends XmlObject>
{
    String getDataType();

    /** Brief description of the types of objects this class imports */
    String getDescription();

    void process(@Nullable PipelineJob job, ImportContext<DocumentRoot> ctx, VirtualFile root) throws Exception;

    @NotNull
    Collection<PipelineJobWarning> postProcess(ImportContext<DocumentRoot> ctx, VirtualFile root) throws Exception;

    /**
     * Map of children data type names to boolean indicating if it is valid for the given import archive context.
     * @param ctx
     * @return Map
     * @throws ImportException
     */
    default @Nullable Map<String, Boolean> getChildrenDataTypes(ImportContext ctx) throws ImportException
    {
        return null;
    }

    /**
     * Validate if the folder importer is valid for the given import context. Default to true.
     * @return boolean
     */
    default boolean isValidForImportArchive(ImportContext<DocumentRoot> ctx) throws ImportException
    {
        return true;
    }

    default ImportContext getImporterSpecificImportContext(String archiveFilePath, User user, Container container) throws IOException
    {
        return null;
    }
}
