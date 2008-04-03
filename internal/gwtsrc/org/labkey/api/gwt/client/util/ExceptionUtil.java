package org.labkey.api.gwt.client.util;

import com.google.gwt.user.client.rpc.SerializableException;
import com.google.gwt.user.client.Window;

/**
 * User: brittp
 * Date: Feb 2, 2007
 * Time: 2:47:50 PM
 */
public class ExceptionUtil
{
    public static SerializableException convertToSerializable(Throwable t)
    {
        return new SerializableException(t.toString() + ": " + t.getMessage());
    }

    public static void showDialog(Throwable caught)
    {
        Window.alert("There was an error making a request from the server: " + caught);
    }
}
