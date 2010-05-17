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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
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
    public static final String ERROR_ROW_NUMBER_KEY = "_rowNumber";

    private List<ValidationError> _errors = new ArrayList<ValidationError>();

    public ValidationException()
    {
    }

    public ValidationException(List<ValidationError> errors)
    {
        _errors = errors;
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

    public String getMessage()
    {
        return toString();
    }


    public static int getRowNum(Map rowErrors)
    {
        if (rowErrors.containsKey(ERROR_ROW_NUMBER_KEY))
        {
            Object _rowNumber = rowErrors.get(ERROR_ROW_NUMBER_KEY);
            if (_rowNumber instanceof Integer)
                return ((Integer)_rowNumber).intValue();
            try
            {
                return Integer.parseInt(String.valueOf(_rowNumber));
            }
            catch (NumberFormatException _) { }
        }

        return -1;
    }

    // converts the List of error Maps into a ValidationException
    public static void throwValidationException(List<Map<String, String>> errors, boolean multipleRows) throws ValidationException
    {
        List<ValidationError> list = new ArrayList<ValidationError>(errors.size());
        for (Map<String, String> rowErrors : errors)
        {
            int row = getRowNum(rowErrors);
            for (Map.Entry<String, String> fields : rowErrors.entrySet())
            {
                String property = fields.getKey();
                if (ERROR_ROW_NUMBER_KEY.equals(property))
                    continue;

                StringBuilder message = new StringBuilder();
                if (multipleRows && row > -1)
                    message.append("Row ").append(row).append(" has error: ");

                if (property != null)
                    message.append(String.valueOf(property)).append(": ");

                message.append(String.valueOf(fields.getValue()));

                if (property != null)
                    list.add(new PropertyValidationError(message.toString(), property));
                else
                    list.add(new SimpleValidationError(message.toString()));
            }
        }
        throw new ValidationException(list);
    }
}
