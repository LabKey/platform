/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;

// DatasetDataWriter actually writes all dataset data (study, assay, and sample type).  This is a do-nothing writer to get the
// assay dataset data checkbox to show up in the UI.
public class AssayDatasetData implements InternalStudyWriter
{
    @Override
    public @Nullable String getDataType()
    {
        return StudyArchiveDataTypes.ASSAY_DATASET_DATA;
    }

    @Override
    public void write(StudyImpl object, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
    }

    @Override
    public boolean includeWithTemplate()
    {
        return false;
    }

    @Override
    public boolean selectedByDefault(AbstractFolderContext.ExportType type, boolean forTemplate)
    {
        return !forTemplate;
    }
}
