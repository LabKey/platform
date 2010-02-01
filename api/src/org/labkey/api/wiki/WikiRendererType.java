/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.wiki;

import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;

/**
 * User: Tamra Myers
 * Date: Aug 17, 2006
 * Time: 6:09:38 PM
 */
public enum WikiRendererType
{
    RADEOX
        {
            public String getDisplayName() {return "Wiki Page";}

            @Override
            public HttpView getSyntaxHelpView()
            {
                return new JspView("/org/labkey/wiki/view/wikiRadeoxHelp.jsp");
            }
        },
    HTML
        {
            public String getDisplayName() {return "HTML";}

            @Override
            public HttpView getSyntaxHelpView()
            {
                HttpView view = new JspView("/org/labkey/wiki/view/wikiHtmlHelp.jsp");
                view.addObject("useVisualEditor", false);
                return view;
            }

            public String getContentType()
            {
                return "text/html";
            }
        },
    TEXT_WITH_LINKS
        {
            public String getDisplayName() {return "Plain Text";}
        };

    public abstract String getDisplayName();

    public String getContentType()
    {
        return "text/plain";
    }

    public HttpView getSyntaxHelpView()
    {
        return new HtmlView("");  // No syntax help by default
    }
}
