/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Represents a potential source for study artifacts to be created and reloaded automatically through the
 * normal study reload framework. The source of the study artifacts could be an external repository or server
 * that is being used as a synchronization source for a LabKey server.
 *
 * Created by klum on 1/16/2015.
 */
public interface StudyReloadSource
{
    /**
     * Returns the descriptive name
     */
    String getName();

    boolean isEnabled(Container container);

    @Nullable
    ActionURL getManageAction(Container c, User user);

    /**
     * Generate the study reload source artifacts from an external source repository in order for the
     * study reload mechanism to update the source study.
     *
     * @param job the pipeline job that this reload task is running in, useful for adding logging information into.
     * @throws PipelineJobException
     */
    void generateReloadSource(@Nullable PipelineJob job, Study study) throws PipelineJobException;
}
