/*
 * Copyright (c) 2009 LabKey Corporation
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

import javax.servlet.ServletException;

/*
* User: adam
* Date: Aug 13, 2009
* Time: 5:16:58 PM
*/

// Use this to report a configuration issue with server, database, smtp, pipeline, etc.
public class ConfigurationException extends ServletException /* RuntimeException? */ implements SkipMothershipLogging, ErrorRendererProperties
{
    public ConfigurationException(String message)
    {
        super(message);
    }

    public ConfigurationException(String message, Throwable t)
    {
        super(message, t);
    }

    public String getTitle()
    {
        return "500: Configuration Error";
    }

    public String getMessageHtml()
    {
        return "<b style=\"color:red;\">" + getMessage() + "</b><br><br>Contact <a href=\"http://www.labkey.com\">LabKey Software</a> at <a href=\"mailto:info@labkey.com\">info@labkey.com</a> for assistance with this issue.<br>";
    }

    public String getHeading()
    {
        return "Configuration Error";
    }
}
