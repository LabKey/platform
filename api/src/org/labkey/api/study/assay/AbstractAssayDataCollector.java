/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.study.assay;


import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;

import java.io.File;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jul 13, 2007
 */
public abstract class AbstractAssayDataCollector<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AssayFileWriter<ContextType> implements AssayDataCollector<ContextType>
{
    public Map<String, File> uploadComplete(ContextType context, @Nullable ExpRun run) throws ExperimentException
    {
        return context.getUploadedData();
    }

    public AdditionalUploadType getAdditionalUploadType(ContextType context)
    {
        return AdditionalUploadType.UploadRequired;
    }
}
