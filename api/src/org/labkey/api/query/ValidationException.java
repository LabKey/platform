/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
import org.labkey.api.data.RuntimeSQLException;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import java.sql.SQLException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class ValidationException extends Exception implements Iterable<ValidationError>
{
    public static final String ERROR_ROW_NUMBER_KEY = "_rowNumber";
    public static final String ERROR_SCHEMA_KEY = "_schemaName";
    public static final String ERROR_QUERY_KEY = "_queryName";
    public static final String ERROR_ROW_KEY = "_row";

    private Map<String, List<PropertyValidationError>> _fieldErrors = new LinkedHashMap<>();
    private List<SimpleValidationError> _globalErrors = new ArrayList<>();

    private String _schemaName;
    private String _queryName;
    private Map<String, Object> _row;
    private int _rowNumber = -1;

    public ValidationException()
    {
    }

    public ValidationException(ValidationException errors)
    {
        addErrors(errors);
    }

    public ValidationException(String message)
    {
        addError(new SimpleValidationError(message));
    }

    public ValidationException(String message, String property)
    {
        addError(new PropertyValidationError(message, property));
    }

    public ValidationException(Collection<ValidationError> errors)
    {
        addErrors(errors);
    }

    public ValidationException(Collection<ValidationError> errors, int rowNumber)
    {
        addErrors(errors);
        _rowNumber = rowNumber;
    }

    public ValidationException(Map<String, Object> map)
    {
        this(map, null, null, null, -1);
    }

    public ValidationException(Map<String, Object> map, String schemaName, String queryName, Map<String, Object> row, int rowNumber)
    {
        _schemaName = schemaName != null ? schemaName : _getSchemaName(map);
        _queryName = queryName != null ? queryName : _getQueryName(map);
        _row = row != null ? row : _getRow(map);
        _rowNumber = rowNumber > -1 ? rowNumber : _getRowNum(map);
        addErrors(map);
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public Map<String, Object> getRow()
    {
        return _row;
    }

    public void setRow(Map<String, Object> row)
    {
        _row = row;
    }

    public int getRowNumber()
    {
        return _rowNumber;
    }

    public void setRowNumber(int rowNumber)
    {
        _rowNumber = rowNumber;
    }

    public ValidationException fillIn(String schemaName, String queryName, Map<String, Object> row, int rowNumber)
    {
        if (getSchemaName() == null)
            setSchemaName(schemaName);
        if (getQueryName() == null)
            setQueryName(queryName);
        if (getRowNumber() == -1)
            setRowNumber(rowNumber);
        if (getRow() == null)
            setRow(row);
        return this;
    }

    public ValidationException addErrors(ValidationException errors)
    {
        if (errors.getSchemaName() != null && !errors.getSchemaName().equals(getSchemaName()))
            throw new IllegalArgumentException("schemaName doesn't match");

        if (errors.getQueryName() != null && !errors.getQueryName().equals(getQueryName()))
            throw new IllegalArgumentException("queryName doesn't match");

        addErrors(errors.getErrors());
        return this;
    }

    public ValidationException addErrors(Collection<ValidationError> errors)
    {
        for (ValidationError error : errors)
            addError(error);

        return this;
    }

    public ValidationException addErrors(Map<String, Object> map)
    {
        for (Map.Entry<String, Object> fields : map.entrySet())
        {
            String field = fields.getKey();
            if (ERROR_ROW_NUMBER_KEY.equals(field) || ERROR_SCHEMA_KEY.equals(field) || ERROR_QUERY_KEY.equals(field) || ERROR_ROW_KEY.equals(field))
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
                messages = new ArrayList<>(values.length);
                for (Object v : values)
                    messages.add(String.valueOf(v));
            }
            else
                messages = Collections.singletonList(String.valueOf(value));

            for (String message : messages)
            {
                if (field != null)
                    addFieldError(field, message);
                else
                    addGlobalError(message);
            }
        }

        return this;
    }

    public ValidationException addError(ValidationError error)
    {
        if (error instanceof PropertyValidationError)
            addFieldError((PropertyValidationError)error);
        else if (error instanceof SimpleValidationError)
            addGlobalError((SimpleValidationError)error);
        else
            throw new IllegalArgumentException();

        return this;
    }

    private ValidationException addFieldError(PropertyValidationError error)
    {
        String field = error.getProperty();
        if (field == null)
            throw new IllegalArgumentException();

        List<PropertyValidationError> list = _fieldErrors.get(field);
        if (list == null)
            _fieldErrors.put(field, list = new ArrayList<>());
        list.add(error);

        return this;
    }

    public ValidationException addFieldError(String field, String message)
    {
        addFieldError(new PropertyValidationError(message, field));
        return this;
    }

    public ValidationException removeFieldErrors(String field)
    {
        _fieldErrors.remove(field);
        return this;
    }

    public ValidationException addGlobalError(SimpleValidationError error)
    {
        _globalErrors.add(error);
        return this;
    }

    public ValidationException addGlobalError(String message)
    {
        _globalErrors.add(new SimpleValidationError(message));
        return this;
    }

    public ValidationException addGlobalError(SQLException x)
    {
        _globalErrors.add(new SimpleValidationError(x));
        return this;
    }

    public ValidationException addGlobalError(RuntimeSQLException x)
    {
        return addGlobalError(x.getSQLException());
    }

    public int getGlobalErrorCount()
    {
        return _globalErrors.size();
    }

    public SimpleValidationError getGlobalError(int i)
    {
        return _globalErrors.get(i);
    }

    /**
     * Returns a live view over the field errors.
     * @param name The field name.
     * @return A live view over the field errors.
     */
    public List<String> getFieldErrors(final String name)
    {
        if (name == null)
            throw new IllegalArgumentException();

        // For convenience in the script environment, create an empty list.
        List<PropertyValidationError> list = _fieldErrors.get(name);
        if (list == null)
            _fieldErrors.put(name, list = new ArrayList<>());

        final List<PropertyValidationError> wrapped = list;
        return new AbstractList<String>()
        {
            @Override
            public String get(int i)
            {
                PropertyValidationError error = wrapped.get(i);
                if (error != null)
                    return error.getMessage();
                return null;
            }

            @Override
            public int size()
            {
                return wrapped.size();
            }

            @Override
            public boolean add(String message)
            {
                return wrapped.add(new PropertyValidationError(message, name));
            }

            @Override
            public String set(int i, String message)
            {
                PropertyValidationError previous = wrapped.get(i);
                wrapped.set(i, new PropertyValidationError(message, name));
                if (previous != null)
                    return previous.getMessage();
                return null;
            }

            @Override
            public void add(int i, String message)
            {
                wrapped.add(i, new PropertyValidationError(message, name));
            }

            @Override
            public String remove(int i)
            {
                PropertyValidationError existing = wrapped.remove(i);
                if (existing != null)
                    return existing.getMessage();
                return null;
            }
        };
    }

    /**
     * Returns a live view over the global errors.
     * @return A live view over the global errors.
     */
    public List<String> getGlobalErrorStrings()
    {
        return new AbstractList<String>()
        {
            @Override
            public String get(int i)
            {
                SimpleValidationError error = _globalErrors.get(i);
                if (error != null)
                    return error.getMessage();
                return null;
            }

            @Override
            public int size()
            {
                return _globalErrors.size();
            }

            @Override
            public boolean add(String message)
            {
                return _globalErrors.add(new SimpleValidationError(message));
            }

            @Override
            public String set(int i, String message)
            {
                SimpleValidationError previous = _globalErrors.get(i);
                _globalErrors.set(i, new SimpleValidationError(message));
                if (previous != null)
                    return previous.getMessage();
                return null;
            }

            @Override
            public void add(int i, String message)
            {
                _globalErrors.add(i, new SimpleValidationError(message));
            }

            @Override
            public String remove(int i)
            {
                SimpleValidationError existing = _globalErrors.remove(i);
                if (existing != null)
                    return existing.getMessage();
                return null;
            }
        };
    }

    public boolean hasGlobalErrors()
    {
        return _globalErrors.size() > 0;
    }

    public boolean hasFieldErrors()
    {
        for (List<PropertyValidationError> list : _fieldErrors.values())
            if (list.size() > 0)
                return true;

        return false;
    }

    public boolean hasFieldErrors(String name)
    {
        if (_fieldErrors.containsKey(name) && _fieldErrors.get(name).size() > 0)
            return true;

        return false;
    }

    public boolean hasErrors()
    {
        if (hasGlobalErrors() || hasFieldErrors())
            return true;

        return false;
    }

    public Set<String> getFields()
    {
        return _fieldErrors.keySet();
    }

    /**
     * Returns all errors in this ValidationException.
     * @return
     */
    public List<ValidationError> getErrors()
    {
        List<ValidationError> errors = new ArrayList<ValidationError>(_globalErrors);

        for (List<PropertyValidationError> list : _fieldErrors.values())
            for (ValidationError error : list)
                errors.add(error);

        return errors;
    }

    @Override
    public Iterator<ValidationError> iterator()
    {
        return getErrors().iterator();
    }

    /**
     * Returns a formatted string of error messages:
     * <pre>
     * [schema:query:Row #:] global errors
     * field1: field errors
     * field2: field errors
     *
     * </pre>
     * @param separator
     * @param fieldErrorsSeparator
     * @return
     */
    public String toString(String separator, String fieldErrorsSeparator)
    {
        StringBuilder msg = new StringBuilder();

        if (_schemaName != null && _queryName != null)
            msg.append(_schemaName).append(":").append(_queryName).append(":");

        if (msg.length() > 0)
            msg.append(" ");

        int prefixLen = msg.length();

        String sep = "";
        for (SimpleValidationError err : _globalErrors)
        {
            msg.append(sep);
            msg.append(err.getMessage());
            sep = separator;
        }

        String fieldSep = "";
        for (Map.Entry<String, List<PropertyValidationError>> entry : _fieldErrors.entrySet())
        {
            sep = "";
            msg.append(fieldSep);
            msg.append(entry.getKey()).append(": ");
            for (PropertyValidationError error : entry.getValue())
            {
                msg.append(sep);
                msg.append(error.getMessage());
                sep = separator;
            }
            fieldSep = fieldErrorsSeparator;
        }

        if (msg.length() == prefixLen)
            msg.append("(No errors specified)");

        return msg.toString();
    }

    @Override
    public String toString()
    {
        return toString("; ", "\n");
    }

    /**
     * Add all errors into an Errors object suitable for display in an html form.
     */
    public void addToErrors(Errors errors)
    {
        MapBindingResult mapErrors = new MapBindingResult(new HashMap(), errors.getObjectName());

        for (SimpleValidationError validationError : _globalErrors)
        {
            String message = validationError.getMessage();
            if (message == null)
                message = "An error occurred";

            mapErrors.reject(SpringActionController.ERROR_MSG, message);
        }

        for (Map.Entry<String, List<PropertyValidationError>> entry : _fieldErrors.entrySet())
            for (PropertyValidationError error : entry.getValue())
                mapErrors.rejectValue(error.getProperty(), SpringActionController.ERROR_MSG, error.getMessage());

        errors.addAllErrors(mapErrors);
    }

    private static String _getSchemaName(Map<String, Object> rowErrors)
    {
        Object o = rowErrors.get(ERROR_SCHEMA_KEY);
        if (o == null)
            return null;

        return o.toString();
    }

    private static String _getQueryName(Map<String, Object> rowErrors)
    {
        Object o = rowErrors.get(ERROR_QUERY_KEY);
        if (o == null)
            return null;

        return o.toString();
    }

    @Override
    public String getMessage()
    {
        return toString();
    }

    private static Map<String, Object> _getRow(Map<String, Object> rowErrors)
    {
        Object o = rowErrors.get(ERROR_ROW_KEY);
        if (o == null)
            return null;

        if (o instanceof Map)
            return (Map<String, Object>)o;

        return null;
    }

    private static int _getRowNum(Map<String, Object> rowErrors)
    {
        if (rowErrors.containsKey(ERROR_ROW_NUMBER_KEY))
        {
            Object _rowNumber = rowErrors.get(ERROR_ROW_NUMBER_KEY);
            if (_rowNumber instanceof Integer)
                return (Integer)_rowNumber;
            try
            {
                return Integer.parseInt(String.valueOf(_rowNumber));
            }
            catch (NumberFormatException e) { }
        }

        return -1;
    }

}
