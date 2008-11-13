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

package org.labkey.core.admin.sql;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

public class SqlScriptController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SqlScriptController.class);

    public SqlScriptController() throws Exception
    {
        setActionResolver(_actionResolver);
    }


    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig page = super.defaultPageConfig();
        page.setTemplate(PageConfig.Template.Dialog);
        return page;
    }


    public static ActionURL getShowRunningScriptsURL(String moduleName)
    {
        ActionURL url = new ActionURL(ShowRunningScriptsAction.class, ContainerManager.getRoot());
        url.addParameter("moduleName", moduleName);
        return url;
    }


    @RequiresSiteAdmin
    @AllowedDuringUpgrade
    public class ShowRunningScriptsAction extends SimpleViewAction<SqlScriptForm>
    {
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            List<SqlScriptRunner.SqlScript> scripts = SqlScriptRunner.getRunningScripts(form.getModuleName());

            if (scripts.isEmpty())
                HttpView.throwRedirect(PageFlowUtil.urlProvider(AdminUrls.class).getModuleStatusURL());

            ShowRunningScriptsPage page = (ShowRunningScriptsPage) JspLoader.createPage(getViewContext().getRequest(), SqlScriptController.class, "showRunningScripts.jsp");
            page.setWaitForScriptsURL(getWaitForScriptsURL(form.getModuleName()));
            page.setCurrentURL(getViewContext().cloneActionURL());
            page.setScripts(scripts);

            return new JspView(page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    private static ActionURL getWaitForScriptsURL(String moduleName)
    {
        ActionURL url = new ActionURL(WaitForScriptsAction.class, ContainerManager.getRoot());
        url.addParameter("moduleName", moduleName);
        return url;
    }


    @RequiresSiteAdmin
    @AllowedDuringUpgrade
    public class WaitForScriptsAction extends SimpleViewAction<SqlScriptForm>
    {
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            getViewContext().getResponse().setHeader("Cache-Control", "no-cache");

            StringBuilder xml = new StringBuilder();

            xml.append("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
            xml.append("<status>");
            if (SqlScriptRunner.waitForScriptToFinish(form.getModuleName(), 2000))
            {
                xml.append("complete");
            }
            else
            {
                xml.append("incomplete");
            }
            xml.append("</status>");

            getPageConfig().setTemplate(PageConfig.Template.None);

            HtmlView view = new HtmlView(xml.toString());
            view.setFrame(WebPartView.FrameType.NONE);
            view.setContentType("text/xml");

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class SqlScriptForm
    {
        private String _moduleName;

        public String getModuleName()
        {
            return _moduleName;
        }

        public void setModuleName(String moduleName)
        {
            _moduleName = moduleName;
        }
    }
}
