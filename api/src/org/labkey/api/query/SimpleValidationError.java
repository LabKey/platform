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
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.sql.SQLException;
import java.util.Objects;

public class SimpleValidationError implements ValidationError
{
    private final String _message;
    private final Throwable _cause;
    private final ValidationException.SEVERITY _severity;

    public SimpleValidationError(@NotNull String message)
    {
        _message = message;
        _cause = null;
        _severity = ValidationException.SEVERITY.ERROR;
    }

    public SimpleValidationError(@NotNull String message, @NotNull ValidationException.SEVERITY severity)
    {
        _message = message;
        _cause = null;
        _severity = severity;
    }

    public SimpleValidationError(SQLException x)
    {
        _message = x.getMessage();
        _cause = x;
        _severity = null;
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

        return Objects.equals(_message, that._message);
    }

    @Override
    public int hashCode()
    {
        return _message != null ? _message.hashCode() : 0;
    }

    @Override
    public void addToBindException(BindException errors, String errorCode)
    {
        if (ValidationException.SEVERITY.WARN.equals(this._severity))
        {
            String[] codes = {String.valueOf(this._severity)};
            Warning warning = new Warning("Warning", codes, null, getMessage());
            errors.addError(warning);
        }
        else
        {
            errors.reject(errorCode, this.getMessage());
        }
    }

    public static class Warning extends ObjectError
    {
        private String _severity;

        public String getSeverity()
        {
            return getCodes()[0];
        }

        public Warning(String objectName, String[] codes, Object[] arguments, String defaultMessage)
        {
            super(objectName, codes, arguments, defaultMessage);
        }
    }
}
