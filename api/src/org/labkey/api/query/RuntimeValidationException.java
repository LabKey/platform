package org.labkey.api.query;

/**
 * Unchecked ValidationException.
 * Prefer throwing and handling ValidationException over using RuntimeValidationException if possible.
 */
public class RuntimeValidationException extends RuntimeException
{
    private ValidationException _vex;

    public RuntimeValidationException(ValidationException vex)
    {
        _vex = vex;
    }

    public RuntimeValidationException(String message)
    {
        _vex = new ValidationException(message);
    }

    public RuntimeValidationException(String message, String property)
    {
        _vex = new ValidationException(message, property);
    }

    public ValidationException getValidationException()
    {
        return _vex;
    }
}
