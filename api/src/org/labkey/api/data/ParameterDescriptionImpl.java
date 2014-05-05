/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * User: kevink
 * Date: 5/13/13
 */
public class ParameterDescriptionImpl implements ParameterDescription, Serializable
{
    protected final String _name;
    protected final String _uri;
    protected final JdbcType _type;

    public ParameterDescriptionImpl(@NotNull String name, @NotNull JdbcType type, @Nullable String uri)
    {
        _name = name;
        _type = type;
        if (uri == null)
            uri = "#" + name;
        _uri = uri;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getURI()
    {
        return _uri;
    }

    @Override
    public JdbcType getJdbcType()
    {
        return _type;
    }
}
