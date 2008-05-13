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

package org.labkey.api.module;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;

/**
 * User: adam
 * Date: Nov 1, 2007
 * Time: 2:32:29 PM
 */
public class FirstRequestHandler
{
    private static final ArrayList<FirstRequestListener> _listeners = new ArrayList<FirstRequestListener>();

    public static void addFirstRequestListener(FirstRequestListener listener)
    {
        synchronized (_listeners)
        {
            _listeners.add(listener);
        }
    }

    public static void handleFirstRequest(HttpServletRequest request)
    {
        for (FirstRequestListener listener : _listeners)
            listener.handleFirstRequest(request);
    }

    public interface FirstRequestListener
    {
        // Listeners should register themselves in Module constructors.  They will get called:
        //
        // - After Core module is upgraded
        // - Before any other module is upgraded (e.g., at bootstrap time, only core scripts will have run)
        // - Before any startup() methods are called
        //
        // This is a good place to do any last minute handling before the first page appears, e.g., initializing
        // server properties based on the incoming request, registering the login URL, initializing authentication
        // providers, etc.
        public void handleFirstRequest(HttpServletRequest request);
    }
}
