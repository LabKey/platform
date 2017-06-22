/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.query;

import org.apache.log4j.Level;
import org.json.JSONObject;

/**
 * Indicates a non-fatal error parsing query metadata.
 * User: Nick Arnold
 * Date: 3/6/13
 */
public class MetadataParseWarning extends MetadataParseException
{
    public MetadataParseWarning(String message)
    {
        this(message, null, 0, 0);
    }

    public MetadataParseWarning(String message, Throwable cause, int line, int column)
    {
        super(message, cause, line, column);
        _level = Level.WARN_INT;
    }

    public MetadataParseWarning(String queryName, QueryParseException other)
    {
        this(queryName + ":" + other.getMessage(), other.getCause(), other._line, other._column);
    }

    @Override
    public JSONObject toJSON(String metadata)
    {
        JSONObject json = super.toJSON(metadata);
        json.put("type", "xml");
        return json;
    }
}
