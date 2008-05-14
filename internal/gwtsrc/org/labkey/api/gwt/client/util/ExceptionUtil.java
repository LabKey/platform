/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
        if (t instanceof SerializableException)
        {
            return (SerializableException) t;
        }
        return new SerializableException(t.toString() + ": " + t.getMessage());
    }

    public static void showDialog(Throwable caught)
    {
        Window.alert("There was an error making a request from the server: " + caught);
    }
}
