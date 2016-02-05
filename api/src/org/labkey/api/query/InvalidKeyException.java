/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import java.util.Map;
/**
 * This class is thrown if the key(s) provided were not valid database keys
 * User: Dave
 * Date: Jun 9, 2008
 */
public class InvalidKeyException extends Exception
{
    public Map<String,Object> _keys = null;

    public InvalidKeyException(String message)
    {
        super(message);
    }

    public InvalidKeyException(String message, Map<String,Object> keys)
    {
        super(message);
        _keys = keys;
    }

    /**
     * Creates a new InvalidKeyException using a default message
     * @param keys The key values that were invalid (may be null)
     */
    public InvalidKeyException(Map<String,Object> keys)
    {
        super("Invalid key value(s)");
        _keys = keys;
    }

    /**
     * Returns the key values that were invalid, if supplied by the thrower. May return null.
     * @return The key values or null.
     */
    public Map<String, Object> getKeys()
    {
        return _keys;
    }
}