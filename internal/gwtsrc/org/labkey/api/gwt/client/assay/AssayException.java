package org.labkey.api.gwt.client.assay;

import com.google.gwt.user.client.rpc.SerializableException;

/**
 * User: jeckels
 * Date: Jul 17, 2007
 */
public class AssayException extends SerializableException
{
    public AssayException()
    {
        super();
    }

    public AssayException(String message)
    {
        super(message);
    }

    public AssayException(Throwable cause)
    {
        super(cause.toString());
    }
}
