package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

public class SimpleValidationWarning extends SimpleValidationError
{
    private String _severity;
    private static String objectName = "SEVERITY";

    public SimpleValidationWarning(@NotNull String message, String severity)
    {
        super(message);
        _severity = severity;
    }

    @Override
    public void addToBindException(BindException errors, String errorCode)
    {
        String[] codes = {String.valueOf(_severity)};
        ObjectError error = new ObjectError(objectName, codes, null, getMessage());
        errors.addError(error);
    }
}
