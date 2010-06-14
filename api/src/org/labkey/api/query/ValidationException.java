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

import org.labkey.api.action.SpringActionController;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import java.util.*;
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

    private List<ValidationException> _nested = new ArrayList<ValidationException>();
    private List<ValidationError> _errors = new ArrayList<ValidationError>();
    private int _rowNumber = -1;

    public ValidationException()
    {
    }

    public ValidationException(int rowNumber)
    {
        _rowNumber = rowNumber;
    }

    public ValidationException(List<ValidationError> errors)
    {
        _errors = errors;
        _rowNumber = -1;
    }

    public ValidationException(List<ValidationError> errors, int rowNumber)
    {
        _errors = errors;
        _rowNumber = rowNumber;
    }

    public ValidationException(ValidationError error, int rowNumber)
    {
        _errors.add(error);
        _rowNumber = rowNumber;
    }

    public ValidationException(String message)
    {
        this(new SimpleValidationError(message), -1);
    }

    public ValidationException(String message, int rowNumber)
    {
        this(new SimpleValidationError(message), rowNumber);
    }

    public ValidationException(String message, String property)
    {
        this(new PropertyValidationError(message, property), -1);
    }

    public ValidationException(String message, String property, int rowNumber)
    {
        this(new PropertyValidationError(message, property), rowNumber);
    }

    public ValidationException(Map<String, Object> errors)
    {
        this(errors, -1);
    }

    public ValidationException(Map<String, Object> errors, int rowNumber)
    {
        _rowNumber = rowNumber > -1 ? rowNumber : getRowNum(errors);
        for (Map.Entry<String, Object> fields : errors.entrySet())
        {
            String property = fields.getKey();
            if (ERROR_ROW_NUMBER_KEY.equals(property))
                continue;

            Object value = fields.getValue();
            if (value == null)
                continue;

            List<String> messages = null;
            if (value instanceof List)
                messages = (List<String>)value;
            else if (value instanceof Object[])
            {
                Object[] values = (Object[])value;
                messages = new ArrayList<String>(values.length);
                for (Object v : values)
                    messages.add(String.valueOf(v));
            }
            else
                messages = Collections.singletonList(String.valueOf(value));

            for (String message : messages)
            {
                if (property != null)
                    addError(new PropertyValidationError(message, property));
                else
                    addError(new SimpleValidationError(message));
            }
        }
    }

    public int getRowNumber()
    {
        return _rowNumber;
    }
    
    public void addError(ValidationError error)
    {
        _errors.add(error);
    }

    public boolean hasErrors()
    {
        if (_errors.size() > 0)
            return true;
        for (ValidationException nested : getNested())
            if (nested.hasErrors())
                return true;
        return false;
    }

    public List<ValidationError> getErrors()
    {
        return _errors;
    }

    public void addNested(ValidationException nested)
    {
        _nested.add(nested);
    }

    public List<ValidationException> getNested()
    {
        return _nested;
    }

    public String toString(String separator, String nestedSeparator)
    {
        StringBuilder msg = new StringBuilder();
        if (_rowNumber > 0)
            msg.append("Row ").append(_rowNumber).append(": ");

        String sep = "";
        for (ValidationError err : getErrors())
        {
            msg.append(sep);
            msg.append(err.getMessage());
            sep = separator;
        }

        sep = msg.length() > 0 ? nestedSeparator : "";
        for (ValidationException vex : getNested())
        {
            msg.append(sep);
            msg.append(vex.toString(separator, nestedSeparator));
            sep = nestedSeparator;
        }
        
        if (msg.length() == 0)
            msg.append("(No errors specified)");

        return msg.toString();
    }

    public String toString()
    {
        return toString("; ", "\n");
    }

    public String getMessage()
    {
        return toString();
    }

    /**
     * Flatten into an Errors object suitable for display in an html form.
     */
    public Errors toErrors(String objectName)
    {
        MapBindingResult errors = new MapBindingResult(new HashMap(), objectName);
        addToErrors(errors, this);
        return errors;
    }

    private static void addToErrors(Errors errors, ValidationException ex)
    {
        for (ValidationError validationError : ex.getErrors())
        {
            String message = validationError.getMessage();
            if (message == null)
                message = "An error occurred";

            String property = null;
            if (validationError instanceof PropertyValidationError)
                property = ((PropertyValidationError)validationError).getProperty();

            if (property != null)
                errors.rejectValue(property, SpringActionController.ERROR_MSG, message);
            else
                errors.reject(SpringActionController.ERROR_MSG, message);
        }

        for (ValidationException nested : ex.getNested())
            addToErrors(errors, nested);
    }

    /**
     * Flatten into a List of error Maps.
     * Top-level errors will not be converted.
     */
    public List<Map<String, Object>> toList()
    {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (ValidationException nested : getNested())
        {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            for (ValidationError validationError : nested.getErrors())
            {
                String message = validationError.getMessage();
                String property = null;
                if (validationError instanceof PropertyValidationError)
                    property = ((PropertyValidationError)validationError).getProperty();

                Object value = map.get(property);
                if (value == null)
                    map.put(property, message);
                else if (value instanceof String)
                {
                    List<String> messages = new ArrayList<String>();
                    messages.add((String)value);
                    messages.add(message);
                    map.put(property, messages);
                }
                else if (value instanceof List)
                {
                    ((List<String>)value).add(message);
                }
            }

            if (!map.isEmpty())
            {
                if (nested.getRowNumber() > -1)
                    map.put(ERROR_ROW_NUMBER_KEY, nested.getRowNumber());
                list.add(map);
            }
        }
        return list;
    }

    public static ValidationException fromList(List<Map<String, Object>> errors)
    {
        ValidationException result = new ValidationException();
        for (Map<String, Object> rowError : errors)
            result.addNested(new ValidationException(rowError));
        return result;
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

}
