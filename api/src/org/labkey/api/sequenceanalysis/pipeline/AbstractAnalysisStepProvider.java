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

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 2:44 PM
 */
abstract public class AbstractAnalysisStepProvider<StepType extends AnalysisStep> extends AbstractPipelineStepProvider<StepType>
{
    public AbstractAnalysisStepProvider(String name, String label, String toolName, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL)
    {
        super(name, label, toolName, description, parameters, clientDependencyPaths, websiteURL);
    }
}
