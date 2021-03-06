/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
* Time: 10:40:29 AM
*/

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.HelpTopic;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.sql.SQLException;
import java.util.Objects;

public class SimpleValidationError implements ValidationError
{
    private final String _message;
    private final Throwable _cause;
    private final ValidationException.SEVERITY _severity;
    private final String _fieldName;
    private final @Nullable HelpTopic _help;

    public SimpleValidationError(@NotNull String message)
    {
        this(message, null, ValidationException.SEVERITY.ERROR, null);
    }

    public SimpleValidationError(@NotNull String message, @NotNull String fieldName, @NotNull ValidationException.SEVERITY severity)
    {
        this(message, fieldName, severity, null);
    }

    public SimpleValidationError(@NotNull String message, @NotNull String fieldName, @NotNull ValidationException.SEVERITY severity, @Nullable HelpTopic help)
    {
        _message = message;
        _cause = null;
        _severity = severity;
        _fieldName = fieldName;
        _help = help;
    }

    public SimpleValidationError(SQLException x)
    {
        _message = x.getMessage();
        _cause = x;
        _severity = ValidationException.SEVERITY.ERROR;
        _fieldName = null;
        _help = null;
    }

    @Override
    public String getMessage()
    {
        return _message;
    }

    public Throwable getCause()
    {
        return _cause;
    }

    @Override
    public ValidationException.SEVERITY getSeverity()
    {
        return _severity;
    }

    public HelpTopic getHelp()
    {
        return _help;
    }

    @Override
    public String toString()
    {
        return getMessage();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleValidationError that = (SimpleValidationError) o;

        return Objects.equals(_message, that._message) && Objects.equals(_severity, that._severity);
    }

    @Override
    public int hashCode()
    {
        return _message != null ? _message.hashCode() : 0;
    }

    @Override
    public void addToBindException(BindException errors, String errorCode, boolean includeWarnings)
    {
        // Only bind warnings when there is a request from client
        if (ValidationException.SEVERITY.WARN.equals(this._severity))
        {
            if (includeWarnings)
            {
                // client (ui-components) distinguishes between server side warnings and client side warnings based on the below objectName passed in the constructor
                FieldWarning fieldWarning = new FieldWarning("ServerWarning", this._fieldName, getMessage(), _help);
                errors.addError(fieldWarning);
            }
        }
        else
        {
            errors.reject(errorCode, this.getMessage());
        }
    }

    public static class FieldWarning extends FieldError
    {
        private final HelpTopic _help;

        public FieldWarning(String objectName, String field, String defaultMessage, @Nullable HelpTopic help)
        {
            super(objectName, field, defaultMessage);
            _help = help;
        }

        public String getSeverity()
        {
            return ValidationException.SEVERITY.WARN.toString();
        }

        public @Nullable HelpTopic getHelp()
        {
            return _help;
        }
    }
}
