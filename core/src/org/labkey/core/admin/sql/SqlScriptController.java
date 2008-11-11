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

import org.apache.log4j.Logger;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

public class SqlScriptController extends SpringActionController
{
    private static Logger _log = Logger.getLogger(SqlScriptController.class);

    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(SqlScriptController.class);

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
    public class ShowRunningScriptsAction extends SimpleViewAction<SqlScriptForm>
    {
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            List<SqlScriptRunner.SqlScript> scripts = SqlScriptRunner.getRunningScripts(form.getModuleName());

            if (scripts.isEmpty())
                HttpView.throwRedirect(PageFlowUtil.urlProvider(AdminUrls.class).getModuleStatusURL());

            ShowRunningScriptsPage page = (ShowRunningScriptsPage) JspLoader.createPage(getViewContext().getRequest(), SqlScriptController.class, "showRunningScripts.jsp");
            page.setWaitForScriptsUrl(getWaitForScriptsURL(form.getModuleName()));
            page.setProvider(form.getProvider());
            page.setCurrentUrl(getViewContext().cloneActionURL());
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
        private String fileName;
        private String moduleName;
        private String schemaName;
        private String to;
        private String from;
        private boolean recommend = true;
        private boolean finish;
        private boolean allowMultipleSubmits = false;

        public String getFrom()
        {
            return from;
        }

        public String getFormattedFrom()
        {
            return ModuleContext.formatVersion(from);
        }

        public void setFrom(String from)
        {
            this.from = from;
        }

        public String getTo()
        {
            return to;
        }

        public String getFormattedTo()
        {
            return ModuleContext.formatVersion(to);
        }

        public void setTo(String to)
        {
            this.to = to;
        }

        public String getFileName()
        {
            return fileName;
        }

        public void setFileName(String fileName)
        {
            this.fileName = fileName;
        }

        public String getSchemaName()
        {
            return schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            this.schemaName = schemaName;
        }

        public String getModuleName()
        {
            return moduleName;
        }

        public void setModuleName(String moduleName)
        {
            this.moduleName = moduleName;
        }

        public SqlScriptProvider getProvider()
        {
            return new FileSqlScriptProvider((DefaultModule)ModuleLoader.getInstance().getModule(moduleName));
        }

        public boolean shouldRecommend()
        {
            return recommend;
        }

        public void setRecommend(boolean recommend)
        {
            this.recommend = recommend;
        }

        public boolean shouldFinish()
        {
            return finish;
        }

        public void setFinish(boolean finish)
        {
            this.finish = finish;
        }

        public boolean getAllowMultipleSubmits()
        {
            return allowMultipleSubmits;
        }

        public void setAllowMultipleSubmits(boolean allowMultipleSubmits)
        {
            this.allowMultipleSubmits = allowMultipleSubmits;
        }
    }
}
