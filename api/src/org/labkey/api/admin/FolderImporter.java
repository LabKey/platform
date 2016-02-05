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
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;

import java.util.Collection;


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

    @Nullable Collection<String> getChildrenDataTypes();
}
