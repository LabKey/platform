/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager.GroupListener;
import org.labkey.api.security.UserPrincipal;
import org.labkey.issue.model.IssueManager;

import java.beans.PropertyChangeEvent;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 3:32:01 PM
 */
public class IssueGroupListener implements GroupListener
{
    // Any change to a group could change assigned to lists
    @Override
    public void principalAddedToGroup(Group g, UserPrincipal user)
    {
        IssueManager.uncache();
    }

    @Override
    public void principalDeletedFromGroup(Group g, UserPrincipal user)
    {
        IssueManager.uncache();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
