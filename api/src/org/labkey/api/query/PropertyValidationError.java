/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

/*
* User: Dave
* Date: Jun 10, 2008
* Time: 10:42:11 AM
*/

/**
 * Represents a validation error for a given property name. Use this when a property of a bean or form is not valid, as
 * the client will be able to display the error message next to the relevant user interface control.
 */
public class PropertyValidationError extends SimpleValidationError
{
    private final String _property;

    public PropertyValidationError(String message, String property)
    {
        super(message);
        _property = property;
    }

    public String getProperty()
    {
        return _property;
    }

    @Override
    public String toString()
    {
        if (_property != null)
            return _property + ": " + getMessage();
        return getMessage();
    }
}
