/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.security;

import java.util.Comparator;

/**
 * User: jeckels
 * Date: Apr 24, 2006
 */
public class UserEmailComparator implements Comparator<User>
{
    public int compare(User a, User b)
    {
        String c1 = a.getEmail();
        String c2 = b.getEmail();
        if (c1 == null ? c2 == null : c1.equals(c2))
            return 0;
        return null == c1 ? -1 : c1.compareTo(c2);
    }
}
