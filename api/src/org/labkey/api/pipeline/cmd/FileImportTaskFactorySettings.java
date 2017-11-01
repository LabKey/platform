/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.pipeline.cmd;

import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.file.FileImportTask;

/**
 * User: tgaluhn
 * Date: 7/31/2017
 */
public class FileImportTaskFactorySettings extends AbstractTaskFactorySettings
{
    private String _cloneName;

    public FileImportTaskFactorySettings(String name)
    {
        super(FileImportTask.class, name);
    }

    public String getCloneName()
    {
        return _cloneName;
    }

    public void setCloneName(String cloneName)
    {
        _cloneName = cloneName;
    }

    @Override
    public TaskId getCloneId()
    {
        return new TaskId(FileImportTask.class, _cloneName);
    }
}
