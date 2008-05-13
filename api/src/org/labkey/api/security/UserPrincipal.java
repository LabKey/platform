/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import java.security.Principal;
import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Sep 20, 2006
 * Time: 1:28:26 PM
 */
public abstract class UserPrincipal implements Principal, Serializable
{
    private String _name;
    private int _userId = 0;

    public static final String typeProject = "g";
    public static final String typeModule = "m";
    public static final String typeUser = "u";

    private String _type;

    protected UserPrincipal(String type)
    {
        _type = type;
    }

    protected UserPrincipal(String name, int id, String type)
    {
        this(type);
        _name = name;
        _userId = id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public int getUserId()
    {
        return _userId;
    }

    public void setUserId(int userId)
    {
        _userId = userId;
    }

    public String getType()
    {
        return _type;
    }

    protected void setType(String type)
    {
        if (type.length() != 1 || !"gum".contains(type))
            throw new IllegalArgumentException("Unrecognized type specified.  Must be one of 'u', 'g', or 'm'.");
        _type = type;
    }
}