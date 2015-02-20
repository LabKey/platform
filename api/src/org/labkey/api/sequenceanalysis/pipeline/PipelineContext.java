/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.WorkDirectory;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:21 PM
 */
public interface PipelineContext
{
    public Logger getLogger();

    public PipelineJob getJob();

    public WorkDirectory getWorkDir();

    public SequenceAnalysisJobSupport getSequenceSupport();

    /**
     * This is the directory where most of the work should take place, usually the remote pipeline working folder.
     */
    public File getWorkingDirectory();

    /**
     * This is the directory where the source files were located and where we expect to deposit the files on completion.
     */
    public File getSourceDirectory();
}
