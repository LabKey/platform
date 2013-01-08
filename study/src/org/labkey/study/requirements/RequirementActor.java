/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.study.requirements;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Location;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 2:56:54 PM
 */
public interface RequirementActor<A extends RequirementActor>
{
    Object getPrimaryKey();

    Container getContainer();

    String getGroupName();

    void addMembers(User... users);

    void addMembers(Location location, User... users);

    User[] getMembers();

    User[] getMembers(Location location);

    void removeMembers(User... members);

    void removeMembers(Location location, User... members);

    void deleteAllGroups();

    A create(User user);

    A update(User user);

    void delete();
}