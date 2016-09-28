/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 10/9/13
 */
public interface StepProvider
{
    /**
     * The preferred type name. Used in the transform type attribute
     * @return The name
     */
    String getName();

    /**
     *  Optional list of alternate names for the type
     *  For example, SimpleQueryTransformStep is the default for a blank (null) value,
     *  and in the initial rollout we were using fully qualified class names
     * @return  List of alternate names
     */
    List<String> getLegacyNames();

    /**
     *  The class for the Step
     * @return The .class
     */
    Class getStepClass();

    /**
     * Instantiate the correct StepMeta subclass
     * @return An instance of the StepMeta corresponding to the type
     */
    StepMeta createMetaInstance();

    /**
     * Instantiate the correct Step subclass.
     * @param f The factory
     * @param job The current job
     * @param meta Should be cast to the correct StepMeta subclass in the constructor call
     * @param context Job context passed into the step
     * @return An instance of the Step class corresponding to the type
     */
    TransformTask createStepInstance(TransformTaskFactory f, PipelineJob job, StepMeta meta, TransformJobContext context);

    Map<String, StepProvider> getNameProviderMap();
}
