/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.ValidationException;
import org.labkey.api.view.ViewContext;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/*
* User: Karl Lum
* Date: Jan 7, 2009
* Time: 5:13:12 PM
*/

/**
 * Used to process input and output data between the server and externally executed qc and analysis scripts.
 */
public interface DataExchangeHandler
{
    public File createValidationRunInfo(AssayRunUploadContext context, ExpRun run, File scriptDir) throws Exception;
    public void processValidationOutput(File runInfo) throws ValidationException;
    public void createSampleData(@NotNull ExpProtocol protocol, ViewContext viewContext, File scriptDir) throws Exception;

    public File createTransformationRunInfo(AssayRunUploadContext context, File scriptDir) throws Exception;
    public TransformResult processTransformationOutput(AssayRunUploadContext context, File runInfo) throws ValidationException;
}