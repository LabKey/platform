/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.writer.ContainerUser;

import java.util.HashSet;
import java.util.Set;

/*
* User: adam
* Date: Apr 27, 2011
* Time: 9:18:14 PM
*/
public class DemoMode
{
    private final static Set<Pair<Container, User>> SET = new HashSet<>();

    // Return an obfuscated version of the id.  This is the choke point for all obfuscation... currently returns
    // a string of astericks the same length as the input.  Null results in an empty string.
    @NotNull
    public static String obfuscate(@Nullable String id)
    {
        return StringUtils.repeat("*", null == id ? 0 : id.length());
    }

    @NotNull
    public static String obfuscate(@Nullable Object o)
    {
        return obfuscate(null == o ? null : o.toString());
    }

    @Nullable
    public static String id(@Nullable String id, Container c, User user)
    {
        if (isDemoMode(c, user))
            return obfuscate(id);
        else
            return id;
    }

    public static boolean isDemoMode(ContainerUser cu)
    {
        return isDemoMode(cu.getContainer(), cu.getUser());
    }

    public static boolean isDemoMode(Container c, User user)
    {
        return SET.contains(new Pair<>(c, user));
    }

    public static void setDemoMode(Container c, User user, boolean setting)
    {
        Pair<Container, User> pair = new Pair<>(c, user);

        if (setting)
            SET.add(pair);
        else
            SET.remove(pair);
    }
}
