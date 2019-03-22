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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.io.File;
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
    Pair<File, Set<File>> createTransformationRunInfo(AssayRunUploadContext<? extends AssayProvider> context, ExpRun run, File scriptDir, Map<DomainProperty, String> runProperties, Map<DomainProperty, String> batchProperties) throws Exception;
    void createSampleData(@NotNull ExpProtocol protocol, ViewContext viewContext, File scriptDir) throws Exception;

    TransformResult processTransformationOutput(AssayRunUploadContext<? extends AssayProvider> context, File runInfo, ExpRun run, File scriptFile, TransformResult mergeResult, Set<File> inputDataFiles) throws ValidationException;

    DataSerializer getDataSerializer();
    
    interface DataSerializer
    {
        /**
         * Called to save or import transformed or QC'd run data to the specified reader or writer.
         */
        void exportRunData(ExpProtocol protocol, List<Map<String, Object>> data, File runData) throws Exception;
        List<Map<String, Object>> importRunData(ExpProtocol protocol, File runData) throws Exception;
    }
}