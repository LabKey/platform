/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

/**
 * Different types of entities that can interact with the system, such as users and groups.
 * User: adam
 * Date: 1/14/12
 */
public enum PrincipalType
{
    USER('u', "User"),
    GROUP('g', "Group"),
    ROLE('r', "Role"),
    MODULE('m', "Module Group"),
    SERVICE('s', "Service");

    private final char _typeChar;
    private final String _description;

    PrincipalType(char type, String description)
    {
        _typeChar = type;
        _description = description;
    }

    public char getTypeChar()
    {
        return _typeChar;
    }

    public String getDescription()
    {
        return _description;
    }

    public static PrincipalType forChar(char type)
    {
        switch (type)
        {
            case 'u': return USER;
            case 'g': return GROUP;
            case 'r': return ROLE;
            case 'm': return MODULE;
            case 's': return SERVICE;
            default : return null;
        }
    }
}
