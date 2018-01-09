/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.api.util;

import org.jetbrains.annotations.Nullable;

/**
 * Use this to report major configuration issues with server, database, smtp, pipeline, etc.  These problems should
 * represent configuration mistakes (NOT code problems) that prevent normal operation of the server, since the
 * exceptions are not reported to mothership.  The message text encourages administrators to contact LabKey for assistance.
 * User: adam
 * Date: Aug 13, 2009
 */

public class ConfigurationException extends MinorConfigurationException implements ErrorRendererProperties
{
    private final String _advice;

    public ConfigurationException(String message)
    {
        this(message, (String)null);
    }

    /**
     * Separating "message" from "advice" allows us to keep the message in the stack trace short, which allows the
     * more detailed advice to word-wrap.  Also, we may want to format the message and advice differently.
     */
    public ConfigurationException(String message, @Nullable String advice)
    {
        super(message);
        _advice = advice;
    }

    public ConfigurationException(String message, Throwable t)
    {
        this(message, null, t);
    }

    public ConfigurationException(String message, @Nullable String advice, Throwable t)
    {
        super(message, t);
        _advice = advice;
    }

    public String getTitle()
    {
        return "500: Configuration Error";
    }

    public String getMessageHtml()
    {
        return "<b style=\"color:red;\" class=\"exception-message\">" + getMessage() + (null != _advice ? " " + _advice : "") + "</b><br><br>This is a problem with your configuration.  Please contact your local server administrator for assistance, or <a href=\"http://www.labkey.com\">LabKey</a> at <a href=\"mailto:support@labkey.com\">support@labkey.com</a> for operational assistance with correcting the configuration error.<br><br>";
    }

    public String getHeading(boolean startup)
    {
        return "Configuration Error Detected" + (startup ? " at LabKey Server Startup" : "");
    }
}
