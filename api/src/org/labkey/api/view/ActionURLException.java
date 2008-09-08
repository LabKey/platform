/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.view;

/**
 * User: adam
 * Date: Sep 8, 2008
 * Time: 11:33:03 AM
 */

// This can be used in places where we fail to convert user input or a URL parameter into a valid ActionURL (e.g., a form).
// Often this is a malformed URL in a custom form built in a wiki; we want to give the form designer a clear exception but
// want to avoid sending to mothership.
public class ActionURLException extends RuntimeException
{
    public ActionURLException(String urlString, Exception e)
    {
        this(urlString, "the URL", e);
    }

    public ActionURLException(String urlString, String urlName, Exception e)
    {
        super("Could not convert \"" + urlString + "\" to a valid ActionURL.  Please ensure that " + urlName + " is well-formed and properly encoded.", e);
    }
}
