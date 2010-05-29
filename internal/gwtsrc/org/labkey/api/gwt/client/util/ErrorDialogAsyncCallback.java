package org.labkey.api.gwt.client.util;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.StatusCodeException;

/**
 * User: jeckels
 * Date: May 28, 2010
 */
public abstract class ErrorDialogAsyncCallback<Type> implements AsyncCallback<Type>
{
    private final String _description;

    public ErrorDialogAsyncCallback()
    {
        this(null);
    }

    protected ErrorDialogAsyncCallback(String description)
    {
        _description = description;
    }

    public final void onFailure(Throwable caught)
    {
        String message = null;
        if (caught instanceof StatusCodeException)
        {
            StatusCodeException statusCodeException = (StatusCodeException)caught;
            switch (statusCodeException.getStatusCode())
            {
                case 401:
                    message = "You do not have permission to perform this operation. Your session may have expired.";
                    break;
                case 404:
                    message = "Not found.";
                    break;
            }
        }
        if (message == null)
        {
            message = caught.getMessage() == null ? caught.toString() : caught.getMessage();
        }
        if (_description != null)
        {
            message = _description + ": " + message;
        }
        reportFailure(message, caught);
        handleFailure(message, caught);
    }

    /** Shows the error message to the user in a dialog */
    protected void reportFailure(String message, Throwable caught)
    {
        Window.alert(message);
    }

    /** Subclasses can override to provide additional error handling */
    protected void handleFailure(String message, Throwable caught)
    {

    }
}
