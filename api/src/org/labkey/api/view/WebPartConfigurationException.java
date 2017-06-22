/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.util.SkipMothershipLogging;

/**
 * Throw to indicate that a web part is incompletely or incorrectly configured, such as pointing a resource that
 * no longer exists.
 * User: adam
 * Date: Aug 10, 2009
 */

public class WebPartConfigurationException extends Exception implements SkipMothershipLogging
{
    public WebPartConfigurationException(BaseWebPartFactory factory, String message)
    {
        super(constructMessage(factory, message));
    }

    public WebPartConfigurationException(BaseWebPartFactory factory, String message, Throwable cause)
    {
        super(constructMessage(factory, message), cause);
    }

    private static String constructMessage(BaseWebPartFactory factory, String details)
    {
        return factory.getName() + " web part is not configured properly: " + details;
    }
}
