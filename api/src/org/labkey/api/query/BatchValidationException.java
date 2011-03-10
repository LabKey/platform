package org.labkey.api.query;

import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of ValidationExceptions, one for each row.
 * Each ValidationException may in turn have multiple field or global errors.
 *
 * User: kevink
 * Date: Mar 9, 2011
 */
public class BatchValidationException extends Exception
{
    List<ValidationException> rowErrors;

    public BatchValidationException()
    {
        super();
        this.rowErrors = new ArrayList<ValidationException>();
    }

    public BatchValidationException(List<ValidationException> rowErrors)
    {
        super();
        this.rowErrors = rowErrors;
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

    public void addToErrors(Errors errors)
    {
        for (ValidationException vex : getRowErrors())
            vex.addToErrors(errors);
    }
}
