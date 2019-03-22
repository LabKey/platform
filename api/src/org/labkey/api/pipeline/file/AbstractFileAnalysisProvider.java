/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.module.Module;

import java.io.File;

/**
 * <code>AbstractFileAnalysisProvider</code>
 */
abstract public class AbstractFileAnalysisProvider<P extends AbstractFileAnalysisProtocolFactory,
        T extends TaskPipeline>
    extends PipelineProvider
{
    public AbstractFileAnalysisProvider(String name, Module owningModule)
    {
        super(name, owningModule);
    }

    abstract public P getProtocolFactory(T pipeline);

    abstract public P getProtocolFactory(File file);
}
