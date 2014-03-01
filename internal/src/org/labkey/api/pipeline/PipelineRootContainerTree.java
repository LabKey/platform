/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.view.ActionURL;

/**
 * A ContainerTree subclass that lets its own subclasses know if a container has a pipeline root set.
 * It makes the currently correct assumption that subfolders inherit pipeline roots from their parents if not set.
 * User: jeckels
 * Date: Oct 27, 2009
 */
public abstract class PipelineRootContainerTree extends ContainerTree
{
    public PipelineRootContainerTree(User user, ActionURL url)
    {
        super("/", user, InsertPermission.class, url);
    }
    
    /** Look up the chain until we find one or reach the root */
    private boolean hasPipelineRoot(Container c)
    {
        return PipelineService.get().hasValidPipelineRoot(c);
    }

    protected final void renderCellContents(StringBuilder html, Container c, ActionURL url)
    {
        renderCellContents(html, c, url, hasPipelineRoot(c));
    }

    /** Subclasses must implement this overloaded version */
    protected abstract void renderCellContents(StringBuilder html, Container c, ActionURL url, boolean hasPipelineRoot);
}
