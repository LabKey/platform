/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Represents a potential source for folder and study artifacts to be created and imported automatically through the
 * folder import framework. The source of the folder artifacts could be an external repository or server that is being
 * used as a synchronization source.
 *
 * See PipelineService.registerFolderArchiveSource() and PipelineService.runGenerateFolderArchiveAndImportJob()
 *
 * Note: This is a generic mechanism that can be used to generate and import any folder archive, but, for historical
 * reasons, a study is passed into generateFolderArchive() for convenience and the management page for these reloads is
 * accessed via manage study. When/if we add non-study implementations we could remove all remnants of study legacy.
 *
 * Created by klum on 1/16/2015.
 */
public interface FolderArchiveSource
{
    /**
     * Returns the descriptive name
     */
    String getName();

    boolean isEnabled(Container container);

    @Nullable
    ActionURL getManageAction(Container c, User user);

    /**
     * Generate a folder archive from an external source repository. That archive will be imported to update the target
     * folder.
     *
     * @param job the pipeline job that this task is running in, useful for logging status while generating the archive
     * @param study the study in the current folder
     */
    void generateFolderArchive(@Nullable PipelineJob job, Study study);
}
