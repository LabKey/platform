/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.issue;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.issue.model.IssueListDefCache;
import org.labkey.issue.model.IssueManager;

public class IssueContainerListener implements ContainerManager.ContainerListener
{
    @Override
    public void containerDeleted(Container c, User user)
    {
        IssueManager.purgeContainer(c, user);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        IssueListDefCache.clearCache();
    }
}
