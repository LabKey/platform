/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.springframework.validation.Errors;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A collection of ValidationExceptions, one for each row.
 * Each ValidationException may in turn have multiple field or global errors.
 *
 * User: kevink
 * Date: Mar 9, 2011
 */
public class BatchValidationException extends Exception
{
    Map<String, Object> extraContext;
    List<ValidationException> rowErrors;

    public BatchValidationException()
    {
        super();
        this.rowErrors = Collections.synchronizedList(new ArrayList<ValidationException>());
    }

    public BatchValidationException(List<ValidationException> rowErrors, Map<String, Object> extraContext)
    {
        super();
        this.rowErrors = rowErrors;
        this.extraContext = extraContext;
    }

    public BatchValidationException(ValidationException e)
    {
        this();
        this.rowErrors.add(e);
    }

    public void addRowError(ValidationException vex)
    {
        rowErrors.add(vex);
    }

    public boolean hasErrors()
    {
        return rowErrors.size() > 0;
    }

    public List<ValidationException> getRowErrors()
    {
        return rowErrors;
    }

    public ValidationException getLastRowError()
    {
        if (rowErrors.isEmpty())
            return null;
        return rowErrors.get(rowErrors.size()-1);
    }

    public void addToErrors(Errors errorsTarget)
    {
        for (ValidationException vex : getRowErrors())
            vex.addToErrors(errorsTarget);
    }

    public void setExtraContext(Map<String, Object> extraContext)
    {
        this.extraContext = extraContext;
    }
    
    @Nullable
    public Map<String, Object> getExtraContext()
    {
        return extraContext;
    }

    // mostly for debug code to reuse the object
    public void clear()
    {
        rowErrors.clear();
        extraContext = null;
    }

    @Override
    public String getMessage()
    {
        // Combine our message with any nested messages
        StringBuilder sb = new StringBuilder();
        String message = super.getMessage();
        if (message != null)
        {
            sb.append(message);
        }
        for (ValidationException rowError : rowErrors)
        {
            message = rowError.getMessage();
            if (message != null)
            {
                sb.append("\n");
                sb.append(rowError.getMessage());
            }
        }
        return sb.toString();
    }

    @Override
    public void printStackTrace(PrintStream s)
    {
        // Combine our stack trace with that of any nested exception
        super.printStackTrace(s);
        for (ValidationException rowError : rowErrors)
        {
            rowError.printStackTrace(s);
        }
    }

    @Override
    public void printStackTrace(PrintWriter s)
    {
        // Combine our stack trace with that of any nested exception
        super.printStackTrace(s);
        for (ValidationException rowError : rowErrors)
        {
            rowError.printStackTrace(s);
        }
    }
}
