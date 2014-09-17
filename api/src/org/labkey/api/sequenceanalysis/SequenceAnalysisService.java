/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.sequenceanalysis;


import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 12:53 PM
 */
abstract public class SequenceAnalysisService
{
    static SequenceAnalysisService _instance;

    public static SequenceAnalysisService get()
    {
        return _instance;
    }

    static public void setInstance(SequenceAnalysisService instance)
    {
        _instance = instance;
    }

    abstract public ReferenceLibraryHelper getLibraryHelper(File refFasta);

    abstract public void registerGenomeTrigger(GenomeTrigger trigger);

    abstract public void registerFileHandler(SequenceFileHandler handler);

    abstract public File createTabixIndex(File input, @Nullable Logger log) throws PipelineJobException;
}
