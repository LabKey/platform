/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.util.HtmlString;
import org.labkey.api.util.SimpleHasHtmlString;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;

/**
 * All of the different types of raw wiki content that can then be rendered to HTML or other views.
 * User: Tamra Myers
 * Date: Aug 17, 2006
 */
public enum WikiRendererType implements SimpleHasHtmlString
{
    RADEOX
        {
            @Override
            public String getDisplayName() {return "Wiki Page";}
            @Override
            public String getFileExtension() {return ".wiki"; }
            @Override
            public HttpView getSyntaxHelpView()
            {
                return new JspView<>("/org/labkey/api/wiki/wikiRadeoxHelp.jsp");
            }
        },
    HTML
        {
            @Override
            public String getDisplayName() {return "HTML";}
            @Override
            public String getFileExtension() {return ".html"; }
            @Override
            public String getContentType()
            {
                return "text/html";
            }
            @Override
            public HttpView getSyntaxHelpView()
            {
                // Note: UseVisualEditor is always false -- remove or fix?
                return new JspView<>("/org/labkey/api/wiki/wikiHtmlHelp.jsp", false);
            }
        },
    TEXT_WITH_LINKS
        {
            @Override
            public String getDisplayName() {return "Plain Text";}
            @Override
            public String getFileExtension() {return ".txt"; }
        },
    MARKDOWN
        {
            @Override
            public String getDisplayName() {return "Markdown";}
            @Override
            public String getFileExtension() {return ".md";}
            @Override
            public HttpView getSyntaxHelpView(){
                return new JspView<>("/org/labkey/api/wiki/wikiMarkdownHelp.jsp");
            }
        };

    public abstract String getDisplayName();

    public abstract String getFileExtension();

    public String getContentType()
    {
        return "text/plain";
    }

    public HttpView getSyntaxHelpView()
    {
        return new HtmlView(HtmlString.EMPTY_STRING);  // No syntax help by default
    }

    public static WikiRendererType getType(String filename)
    {
        for (WikiRendererType wikiRendererType : WikiRendererType.values())
        {
            if (filename.toLowerCase().endsWith(wikiRendererType.getFileExtension().toLowerCase()))
            {
                return wikiRendererType;
            }
        }
        // Default to HTML
        return WikiRendererType.HTML;
    }

    public String getDocumentName(String wikiName)
    {
        return wikiName + getFileExtension();
    }
}
