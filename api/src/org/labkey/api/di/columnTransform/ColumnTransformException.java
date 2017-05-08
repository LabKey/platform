package org.labkey.api.di.columnTransform;

import org.jetbrains.annotations.NotNull;

/**
 * Wrapping class for exceptions caught in the Supplier to ColumnTransform.addOutputColumn(), such as implementations of
 * ColumnTransform.doTransform(). Must be a RuntimeException b/c Java 8 Suppliers don't directly
 * support throwing checked exceptions.
 *
 * A thrown instance of this exception will be automatically unwrapped for the ETL log to show the cause and stack
 * trace of the original underlying exception.
 */
public class ColumnTransformException extends RuntimeException
{
    /**
     *
     * @param message An explanatory message, as with any other thrown exception
     * @param cause The caught exception (don't unwrap in the call to the constructor; pass the exception as-is)
     */
    public ColumnTransformException(String message, @NotNull Throwable cause)
    {
        super(message, cause);
    }

    /**
     *
     * @param cause The caught exception (don't unwrap in the call to the constructor; pass the exception as-is)
     */
    public ColumnTransformException(@NotNull Throwable cause)
    {
        super(cause);
    }
}
