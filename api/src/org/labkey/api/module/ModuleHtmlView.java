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
package org.labkey.api.module;

import org.labkey.api.view.HtmlView;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.util.Cache;
import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/*
* User: Dave
* Date: Jan 23, 2009
* Time: 4:48:17 PM
*/

/**
 * Html view based on HTML source stored in a module
 */
public class ModuleHtmlView extends HtmlView
{
    private static Cache _viewdefCache = new Cache(1024, Cache.HOUR);

    private ModuleHtmlViewDefinition _viewdef = null;

    public ModuleHtmlView(File htmlFile)
    {
        super(null);
        _viewdef = getViewDef(htmlFile);
        setTitle(_viewdef.getTitle());
        setHtml(replaceTokens(_viewdef.getHtml()));
        if(null != _viewdef.getFrameType())
            setFrame(_viewdef.getFrameType());
    }

    public String replaceTokens(String html)
    {
        ViewContext context = getViewContext();
        String ret = html.replaceAll("<%=\\s*contextPath\\s*%>", context.getContextPath());
        ret = ret.replaceAll("<%=\\s*containerPath\\s*%>", context.getContainer().getPath());
        return ret;
    }

    public static ModuleHtmlViewDefinition getViewDef(File htmlFile)
    {
        ModuleHtmlViewDefinition viewdef = (ModuleHtmlViewDefinition)_viewdefCache.get(htmlFile.getAbsolutePath());
        if(null == viewdef || viewdef.isStale())
        {
            viewdef = new ModuleHtmlViewDefinition(htmlFile);
            _viewdefCache.put(htmlFile.getAbsolutePath(), viewdef);
        }
        return viewdef;
    }

    public PageConfig.Template getPageTemplate()
    {
        return _viewdef.getPageTemplate();
    }

    public boolean isRequiresLogin()
    {
        return _viewdef.isRequiresLogin();
    }

    public int getRequiredPerms()
    {
        return _viewdef.getRequiredPerms();
    }
}