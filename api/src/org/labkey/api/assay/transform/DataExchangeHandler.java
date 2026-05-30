/*
 * Copyright (c) 2009-2026 LabKey Corporation
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
package org.labkey.api.assay.transform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Used to process input and output data between the server and externally executed qc and analysis scripts.
 */
public interface DataExchangeHandler
{
    /**
     * Create and serialize the run properties information that is made available to transform scripts.
     * The file contains a variety of information based on the transform operation being specified.
     *
     * @param operation The transform operation being performed
     * @param context Contains information about the import or update context
     * @param scriptDir The folder that the transform script will be run in.
     * @return The map of the run properties file to the set of other data files associated with the operation
     * being performed.
     */
    Pair<FileLike, Set<FileLike>> createTransformationRunInfo(
            DataTransformService.TransformOperation operation,
            AssayRunUploadContext<? extends AssayProvider> context,
            @Nullable ExpRun run,
            FileLike scriptDir,
            Map<DomainProperty, String> runProperties,
            Map<DomainProperty, String> batchProperties
    ) throws Exception;

    /**
     * Creates a test version of the run properties file for download
     */
    void createSampleData(
            DataTransformService.TransformOperation operation,
            @NotNull ExpProtocol protocol,
            ViewContext viewContext,
            FileLike scriptDir
    ) throws Exception;

    TransformResult processTransformationOutput(
            DataTransformService.TransformOperation operation,
            AssayRunUploadContext<? extends AssayProvider> context,
            FileLike runInfo,
            @Nullable ExpRun run,
            FileLike scriptFile,
            TransformResult mergeResult,
            Set<FileLike> inputDataFiles
    ) throws ValidationException;

    DataSerializer getDataSerializer();
    
    interface DataSerializer
    {
        /**
         * Called to save or import transformed or QC'd run data to the specified reader or writer.
         */
        void exportRunData(ExpProtocol protocol, List<DataIteratorBuilder> data, FileLike runData, TSVWriter tsvWriter) throws IOException, BatchValidationException;

        DataIteratorBuilder importRunData(ExpProtocol protocol, File runData) throws Exception;
    }
}