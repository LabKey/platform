/*
 * Copyright (c) 2008 LabKey Corporation
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
/*
 * User: Dave
 * Date: Jun 9, 2008
 * Time: 5:01:36 PM
 */

/**
 * This class is thrown when the provided keys for a new data object already
 * exist in the database.
 */
public class DuplicateKeyException extends Exception
{
    private Map<String,Object> _keys;

    public DuplicateKeyException(String message)
    {
        super(message);
    }

    public DuplicateKeyException(String message, Map<String,Object> keys)
    {
        super(message);
        _keys = keys;
    }

    public Map<String, Object> getKeys()
    {
        return _keys;
    }
}