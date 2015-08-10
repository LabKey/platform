/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.run;

import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/19/2014
 * Time: 9:34 AM
 */
public interface CommandWrapper
{
    /**
     *
     * @param params A list of params used to create the command.  This will be passed directly to ProcessBuilder()
     * @return The output of this command.
     * @throws PipelineJobException
     */
    String execute(List<String> params) throws PipelineJobException;

    String execute(List<String> params, File stdout) throws PipelineJobException;

    List<String> getCommandsExecuted();
}
