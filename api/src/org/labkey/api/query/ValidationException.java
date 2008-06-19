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

import java.util.List;
import java.util.ArrayList;
/*
 * User: Dave
 * Date: Jun 9, 2008
 * Time: 4:49:33 PM
 */

/**
 * This class is thrown if there were validation errors during a save.
 * This class is essentially a container for objects that implement
 * ValidationError, so use the <code>getErrors()</code> method to
 * retrieve individual validation errors. The <code>toString()</code>
 * method will simply concatenate all the error messages together,
 * separated by semi-colons.
 */
public class ValidationException extends Exception
{
    private List<ValidationError> _errors = new ArrayList<ValidationError>();

    public ValidationException()
    {
    }

    public ValidationException(String message)
    {
        _errors.add(new SimpleValidationError(message));
    }

    public ValidationException(String message, String property)
    {
        _errors.add(new PropertyValidationError(message, property));
    }

    public void addError(ValidationError error)
    {
        _errors.add(error);
    }

    public List<ValidationError> getErrors()
    {
        return _errors;
    }

    public String toString(String separator)
    {
        StringBuilder msg = new StringBuilder();
        String sep = "";
        for(ValidationError err : getErrors())
        {
            msg.append(sep);
            msg.append(err.getMessage());
            sep = separator;
        }
        if(msg.length() == 0)
            msg.append("(No errors specified)");

        return msg.toString();
    }

    public String toString()
    {
        return toString("; ");
    }
}