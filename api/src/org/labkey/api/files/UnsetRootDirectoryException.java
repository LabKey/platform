/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

package org.labkey.api.files;

import org.labkey.api.data.Container;

/**
 * User: klum
 * Date: Dec 10, 2009
 */
public class UnsetRootDirectoryException extends IllegalStateException
{
    private Container project;

    public UnsetRootDirectoryException(Container project)
    {
        super("No file root has been set for the project " + project.getName());
        this.project = project;
    }

    public Container getProject()
    {
        return project;
    }

    public void setProject(Container project)
    {
        this.project = project;
    }
}
