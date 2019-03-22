/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.exp.pipeline;

import org.labkey.api.pipeline.TaskId;
import org.labkey.api.util.FileType;

/**
 * Pointer for {@link TaskId} for a XAR generator pipeline task, used to create runs and import data files.
 * User: jeckels
 * Date: Jul 28, 2008
 */
public class XarGeneratorId
{
    public static final FileType FT_XAR_XML = new FileType(".xar.xml");
    public static final FileType FT_PIPE_XAR_XML = new FileType(".pipe.xar.xml");

    interface Factory
    {
        FileType[] getInputTypes();

        FileType getOutputType();
    }

    /**
     * Interface for support required from the PipelineJob to run this task,
     * beyond the base PipelineJob methods.
     */
    interface JobSupport
    {
        /**
         * Returns a description of the search.
         */
        String getDescription();
    }
}