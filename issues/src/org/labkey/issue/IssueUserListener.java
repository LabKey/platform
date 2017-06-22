/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.labkey.api.security.User;
import org.labkey.api.security.UserManager.UserListener;
import org.labkey.issue.model.IssueManager;

import java.beans.PropertyChangeEvent;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 3:35:43 PM
 */
public class IssueUserListener implements UserListener
{
    public void userAddedToSite(User user)
    {
        IssueManager.uncache(); // Might change assigned to lists
    }

    public void userDeletedFromSite(User user)
    {
        IssueManager.deleteUserEmailPreferences(user);
        IssueManager.uncache(); // Might change assigned to lists
    }

    public void userAccountDisabled(User user)
    {
        IssueManager.uncache();
    }

    public void userAccountEnabled(User user)
    {
        IssueManager.uncache();
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
