/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.api.qc;

import org.apache.commons.vfs2.FileObject;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Used to process input and output data between the server and externally executed qc and analysis scripts.
 * User: Karl Lum
 * Date: Jan 7, 2009
 */
public interface DataExchangeHandler
{
    Pair<FileObject, Set<FileObject>> createTransformationRunInfo(AssayRunUploadContext<? extends AssayProvider> context, ExpRun run, FileObject scriptDir, Map<DomainProperty, String> runProperties, Map<DomainProperty, String> batchProperties) throws Exception;
    void createSampleData(@NotNull ExpProtocol protocol, ViewContext viewContext, FileObject scriptDir) throws Exception;

    TransformResult processTransformationOutput(AssayRunUploadContext<? extends AssayProvider> context, FileObject runInfo, ExpRun run, FileObject scriptFile, TransformResult mergeResult, Set<FileObject> inputDataFiles) throws ValidationException;

    DataSerializer getDataSerializer();
    
    interface DataSerializer
    {
        /**
         * Called to save or import transformed or QC'd run data to the specified reader or writer.
         */
        void exportRunData(ExpProtocol protocol, List<DataIteratorBuilder> data, FileObject runData) throws IOException, BatchValidationException;

        default void exportRunData(ExpProtocol protocol, DataIteratorBuilder data, FileObject runData) throws IOException, BatchValidationException
        {
            exportRunData(protocol, Collections.singletonList(data), runData);
        }
        DataIteratorBuilder importRunData(ExpProtocol protocol, File runData) throws Exception;
    }
}