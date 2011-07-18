/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

import org.json.JSONObject;

public class QueryException extends RuntimeException
{
    public QueryException(String message)
    {
        super(message);
    }

    public QueryException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Serializes a QueryException into a JSON object.
     *
     * @param sql - the sql that the parse exceptions originated from
     */
    public JSONObject toJSON(String sql)
    {
        JSONObject error = new JSONObject();

        error.put("msg", getMessage());

        return error;
    }
}
