/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.pipeline;

/**
 * Thrown when an executable has a non-zero return/exit code
 * 
 * User: jeckels
 * Date: Jan 7, 2011
 */
public class ToolExecutionException extends PipelineJobException
{
    private final int _exitCode;

    public ToolExecutionException(String message, int exitCode)
    {
        super(message);
        _exitCode = exitCode;
    }

    public int getExitCode()
    {
        return _exitCode;
    }
}
