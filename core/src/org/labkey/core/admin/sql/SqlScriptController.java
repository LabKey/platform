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
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.*;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SqlScriptController extends SpringActionController
{
    private static Logger _log = Logger.getLogger(SqlScriptController.class);

    static DefaultActionResolver _actionResolver = new DefaultActionResolver(SqlScriptController.class);

    public SqlScriptController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig page = super.defaultPageConfig();
        page.setTemplate(PageConfig.Template.Dialog);
        return page;
    }


    public static class SqlScriptUrls implements SqlScriptRunner.SqlScriptUrls
    {
        public ActionURL getDefaultURL(ActionURL returnURL, String moduleName, double fromVersion, double toVersion, boolean express)
        {
            if (express)
                return getRunRecommended(returnURL, moduleName, null, fromVersion, toVersion);
            else
                return getShowList(returnURL, moduleName, null, fromVersion, toVersion);
        }

        public ActionURL getRunRecommended(ActionURL returnURL, String moduleName, String schemaName, double fromVersion, double toVersion)
        {
            ActionURL url = createURL(RunRecommendedAction.class, returnURL, moduleName, schemaName, fromVersion, toVersion);
            url.addParameter("finish", "1");
            return url;
        }

        public ActionURL getShowList(ActionURL returnURL, String moduleName, String schemaName, double fromVersion, double toVersion)
        {
            return createURL(ShowListAction.class, returnURL, moduleName, schemaName, fromVersion, toVersion);
        }

        private ActionURL createURL(Class<? extends Controller> actionClass, ActionURL returnURL, String moduleName, String schemaName, double fromVersion, double toVersion)
        {
            ActionURL url = new ActionURL(actionClass, ContainerManager.getRoot());

            url.addParameter("moduleName", moduleName);

            if (null != schemaName)
                url.addParameter("schemaName", schemaName);

            url.addParameter("from", String.valueOf(fromVersion));
            url.addParameter("to", String.valueOf(toVersion));
            url.addParameter("uri", returnURL.getLocalURIString());

            return url;
        }
    }


    @RequiresSiteAdmin
    public class FinishAction extends RedirectAction<SqlScriptForm>
    {
        public ActionURL getSuccessURL(SqlScriptForm form)
        {
            return new ActionURL(getViewContext().getActionURL().getParameter("uri"));
        }

        public boolean doAction(SqlScriptForm form, BindException errors) throws Exception
        {
            DbSchema.invalidateSchemas();
            String moduleName = form.getModuleName();

            if (null != moduleName)
            {
                Module module = ModuleLoader.getInstance().getModule(moduleName);

                if (null != module)
                {
                    ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
                    ctx.getProperties().put(SqlScriptRunner.SCRIPTS_RUN_KEY, Boolean.TRUE);
                }
            }

            return true;
        }

        public void validateCommand(SqlScriptForm target, Errors errors)
        {
        }
    }


    @RequiresSiteAdmin
    public class ShowListAction extends SimpleViewAction<SqlScriptForm>
    {
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            VBox vbox = new VBox();

            HttpView header = new GroovyView("/org/labkey/core/admin/sql/header.gm");
            header.addObject("exception", SqlScriptRunner.getException());
            header.addObject("form", form);
            vbox.addView(header);

            HttpView recommendedScripts = new RecommendedScriptsView(form.shouldRecommend(), form.getProvider(), form.getSchemaName(), Double.parseDouble(form.getFrom()), Double.parseDouble(form.getTo()));
            vbox.addView(recommendedScripts);

            HttpView newScripts = new NewScriptsView(form.getProvider(), form.getSchemaName());
            vbox.addView(newScripts);

            HttpView oldScripts = new OldScriptsView(form.getProvider());
            vbox.addView(oldScripts);

            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class RecommendedScriptsView extends WebPartView
    {
        private List<SqlScriptRunner.SqlScript> _scripts;
        private boolean _shouldRecommend;

        public RecommendedScriptsView(boolean shouldRecommend, SqlScriptRunner.SqlScriptProvider provider, String schemaName, double from, double to) throws SQLException
        {
            super("Recommended scripts from " + provider.getProviderName());
            _scripts = SqlScriptRunner.getRecommendedScripts(provider, schemaName, from, to);
            _shouldRecommend = shouldRecommend;
        }


        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            ActionURL cloneUrl = getViewContext().cloneActionURL().setAction("showScript");
            cloneUrl.deleteParameter("fileName");

            ButtonBar bb = new ButtonBar();

            if (!_shouldRecommend)
                out.print("<br>No recommendations.<br><br>");
            else if (null == _scripts)
                out.print("<br>Error retrieving scripts.<br><br>");
            else if (0 == _scripts.size())
                out.print("<br>All scripts have been run.<br><br>");
            else
            {
                out.print("<br><table class=\"labkey-data-region\">");

                for (SqlScriptRunner.SqlScript script : _scripts)
                    out.print("<tr><td>" + script.getDescription() + "</td></tr>");

                out.print("</table><br>");

                ActionURL runScriptsURL = cloneUrl.clone().replaceParameter("allowMultipleSubmits", "1");
                ActionButton runAll = new ActionButton(runScriptsURL.setAction("runRecommended").getEncodedLocalURIString(), "Run Recommended Scripts");
                runAll.setActionType(ActionButton.Action.LINK);

                runScriptsURL.addParameter("finish", "1");
                ActionButton runAllAndFinish = new ActionButton(runScriptsURL.getEncodedLocalURIString(), "Run Recommended Scripts and Finish");
                runAllAndFinish.setActionType(ActionButton.Action.LINK);

                bb.add(runAllAndFinish);
                bb.add(runAll);
            }

            ActionButton finish = new ActionButton("finish", "Finish");
            finish.setURL(cloneUrl.setAction("finish").getEncodedLocalURIString());
            finish.setActionType(ActionButton.Action.LINK);
            bb.add(finish);

            bb.render(new RenderContext(getViewContext()), out);
        }
    }


    public static class NewScriptsView extends WebPartView
    {
        private List<SqlScriptRunner.SqlScript> _scripts;

        public NewScriptsView(SqlScriptRunner.SqlScriptProvider provider, String schemaName) throws SQLException
        {
            super("New scripts from " + provider.getProviderName());
            _scripts = SqlScriptRunner.getNewScripts(provider, schemaName);
        }


        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            ActionURL cloneUrl = getViewContext().cloneActionURL().setAction("showScript");

            if (null == _scripts)
                out.print("<br>Error");
            else if (0 == _scripts.size())
                out.print("<br>All scripts have been run");
            else
            {
                out.print("<br>");

                // Post back to current URL
                out.print("<form method=\"post\"><table class=\"labkey-data-region\">");

                for (SqlScriptRunner.SqlScript script : _scripts)
                {
                    String fileName = script.getDescription();
                    out.print("<tr><td><input type=checkbox name='fileNames' value=\"" + fileName + "\"></td>");
                    cloneUrl.replaceParameter("fileName", fileName);
                    out.print("<td><a href=\"" + cloneUrl.getEncodedLocalURIString() + "\">" + fileName + "</a></td></tr>");
                }

                out.print("</table><br>");
                cloneUrl.deleteParameter("fileName");

                ActionURL runScripts = cloneUrl.clone().replaceParameter("allowMultipleSubmits", "1");
                ActionButton runAll = new ActionButton(runScripts.setAction("runAll").getEncodedLocalURIString(), "Run All");
                runAll.setActionType(ActionButton.Action.LINK);

                ActionButton runSelected = new ActionButton("runSelected", "Run Selected");
                runSelected.setScript("return verifySelected(this.form, \"" + runScripts.setAction("runSelected").getEncodedLocalURIString() + "\", \"post\", \"scripts\")");
                runSelected.setActionType(ActionButton.Action.GET);

                ActionButton insertAll = new ActionButton(cloneUrl.setAction("insertAll").getEncodedLocalURIString(), "Mark All As \"Run\"");
                insertAll.setActionType(ActionButton.Action.LINK);

                ActionButton insertSelected = new ActionButton("markSelected", "Mark Selected As \"Run\"");
                insertSelected.setScript("return verifySelected(this.form, \"" + cloneUrl.setAction("insertSelected").getEncodedLocalURIString() + "\", \"post\", \"scripts\")");
                insertSelected.setActionType(ActionButton.Action.GET);

                ButtonBar bb = new ButtonBar();
                bb.add(ActionButton.BUTTON_SELECT_ALL);
                bb.add(ActionButton.BUTTON_CLEAR_ALL);
                bb.add(runAll);
                bb.add(runSelected);
                bb.add(insertAll);
                bb.add(insertSelected);

                bb.render(new RenderContext(getViewContext()), out);
                out.print("</form>");
            }
        }
    }


    public static class OldScriptsView extends GridView
    {
        private SqlScriptRunner.SqlScriptProvider _provider;

        public OldScriptsView(SqlScriptRunner.SqlScriptProvider provider)
        {
            super(null);  // No container
            _provider = provider;
        }


        @Override
        protected void prepareWebPart(RenderContext model) throws ServletException
        {
            setTitle("Previously run scripts in " + _provider.getProviderName());
            super.prepareWebPart(model);
        }


        @Override
        public void renderView(RenderContext model, PrintWriter out) throws IOException, ServletException
        {
            ActionURL cloneUrl = model.getViewContext().cloneActionURL();

            DataRegion rgn = new DataRegion();
            rgn.setSelectionKey(DataRegionSelection.getSelectionKey(SqlScriptManager.getTableInfo().getSchema().getName(), SqlScriptManager.getTableInfo().getName(), null, "OldScriptsView"));
            rgn.setShowRecordSelectors(true);
            rgn.setColumns(SqlScriptManager.getTableInfo().getColumns("FileName, Modified, ModifiedBy"));
            rgn.getDisplayColumn(0).setURL(cloneUrl.setAction("showScript").getLocalURIString() + "&fileName=${FileName}");

            ActionButton deleteAll = new ActionButton("deleteAll", "Mark All As \"New\"");
            deleteAll.setURL(cloneUrl.setAction("deleteAll").getEncodedLocalURIString());
            deleteAll.setActionType(ActionButton.Action.LINK);

            ActionButton deleteSelected = new ActionButton("markSelected", "Mark Selected As \"New\"");
            deleteSelected.setScript("return verifySelected(this.form, \"" + cloneUrl.setAction("deleteSelected").getEncodedLocalURIString() + "\", \"post\", \"scripts\")");
            deleteSelected.setActionType(ActionButton.Action.GET);

            ButtonBar bb = new ButtonBar();
            bb.add(deleteAll);
            bb.add(deleteSelected);
            rgn.setButtonBar(bb);
            setDataRegion(rgn);
            setFilter(new SimpleFilter("ModuleName", _provider.getProviderName()));
            super.renderView(model, out);
        }
    }


    private abstract class DeleteAction extends RedirectAction<SqlScriptForm>
    {
        public ActionURL getSuccessURL(SqlScriptForm form)
        {
            return getViewContext().cloneActionURL().setAction("showList");
        }

        public void validateCommand(SqlScriptForm target, Errors errors)
        {
        }
    }


    @RequiresSiteAdmin
    public class DeleteAllAction extends DeleteAction
    {
        public boolean doAction(SqlScriptForm form, BindException errors) throws Exception
        {
            SqlScriptManager.deleteAllScripts(form.getProvider());

            return true;
        }
    }


    @RequiresSiteAdmin
    public class DeleteSelectedAction extends DeleteAction
    {
        public boolean doAction(SqlScriptForm form, BindException errors) throws Exception
        {
            Set<String> list = DataRegionSelection.getSelected(getViewContext(), true);
            List<String> fileNames = new ArrayList<String>(list.size());

            for (String key : list)
            {
                String[] keys = key.split(",");
                fileNames.add(keys[1]);
            }

            SqlScriptManager.deleteSelectedScripts(form.getProvider(), fileNames);

            return true;
        }
    }


    private abstract class RunScriptsAction extends RedirectAction<SqlScriptForm>
    {
        public ActionURL getSuccessURL(SqlScriptForm form)
        {
            ActionURL showRunningScriptsUrl = getViewContext().cloneActionURL().setAction("showRunningScripts");
            showRunningScriptsUrl.deleteParameter("fileNames");
            showRunningScriptsUrl.replaceParameter("recommend", "0");  // Once any script is run, stop recommending

            return showRunningScriptsUrl;
        }

        public void validateCommand(SqlScriptForm target, Errors errors)
        {
        }

        public boolean doAction(SqlScriptForm form, BindException errors) throws Exception
        {
            List<SqlScript> scripts = getScripts(form);
            SqlScriptRunner.runScripts(getViewContext().getUser(), scripts, form.getProvider(), form.getAllowMultipleSubmits());
            return true;
        }

        protected abstract List<SqlScript> getScripts(SqlScriptForm form) throws SQLException;
    }


    @RequiresSiteAdmin
    public class RunAllAction extends RunScriptsAction
    {
        protected List<SqlScript> getScripts(SqlScriptForm form) throws SQLException
        {
            return SqlScriptRunner.getNewScripts(form.getProvider(), form.getSchemaName());
        }
    }


    @RequiresSiteAdmin
    public class RunSelectedAction extends RunScriptsAction
    {
        protected List<SqlScript> getScripts(SqlScriptForm form) throws SQLException
        {
            return getPostedScripts(form.getProvider());
        }
    }


    @RequiresSiteAdmin
    public class RunRecommendedAction extends RunScriptsAction
    {
        protected List<SqlScript> getScripts(SqlScriptForm form) throws SQLException
        {
            return SqlScriptRunner.getRecommendedScripts(form.getProvider(), form.getSchemaName(), Double.parseDouble(form.getFrom()), Double.parseDouble(form.getTo()));
        }
    }


    private List<SqlScript> getPostedScripts(SqlScriptProvider provider)
    {
        List<String> filenames = getViewContext().getList("fileNames");
        List<SqlScriptRunner.SqlScript> scripts = new ArrayList<SqlScriptRunner.SqlScript>(filenames.size());

        for (String filename : filenames)
        {
            SqlScriptRunner.SqlScript script = provider.getScript(filename);

            if (null != script)
                scripts.add(script);
        }

        return scripts;
    }


    @RequiresSiteAdmin
    public class ShowRunningScriptsAction extends SimpleViewAction<SqlScriptForm>
    {
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            List<SqlScriptRunner.SqlScript> scripts = SqlScriptRunner.getRunningScripts();
            if (scripts.isEmpty())
            {
                ActionURL showListUrl = getViewContext().cloneActionURL().setAction("showList");

                // "Run Recommended and Finish" case
                if (form.shouldFinish())
                {
                    Exception ex = SqlScriptRunner.getException();

                    if (null == ex)
                    {
                        FinishAction finish = new FinishAction();
                        finish.doAction(form, errors);
                        HttpView.throwRedirect(getViewContext().getActionURL().getParameter("uri"));
                    }

                    // Display exception and give another chance to run scripts even if we're supposed to finish
                    HttpView view = new GroovyView("/org/labkey/core/admin/sql/errors.gm");
                    view.addObject("exception", ex);
                    view.addObject("providerName", form.getProvider().getProviderName());

                    showListUrl.deleteParameter("finish");
                    view.addObject("showListUrl", showListUrl.getLocalURIString());

                    ActionURL finishUrl = getViewContext().cloneActionURL().setAction("finish");
                    view.addObject("finishUrl", finishUrl.getLocalURIString());

                    return view;
                }

                HttpView.throwRedirect(showListUrl);
            }

            ShowRunningScriptsPage page = (ShowRunningScriptsPage) JspLoader.createPage(getViewContext().getRequest(), SqlScriptController.class, "showRunningScripts.jsp");
            ActionURL waitForScriptsUrl = getViewContext().cloneActionURL().setAction("waitForScripts");
            page.setWaitForScriptsUrl(waitForScriptsUrl);
            page.setProvider(form.getProvider());
            page.setCurrentUrl(getViewContext().cloneActionURL());
            page.setScripts(SqlScriptRunner.getRunningScripts());

            return new JspView(page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class WaitForScriptsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getViewContext().getResponse().setHeader("Cache-Control", "no-cache");

            StringBuilder xml = new StringBuilder();

            xml.append("<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>\n");
            xml.append("<status>");
            if (SqlScriptRunner.waitForScriptToFinish(2000))
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


    private abstract class InsertScriptsAction extends RedirectAction<SqlScriptForm>
    {
        public ActionURL getSuccessURL(SqlScriptForm sqlScriptForm)
        {
            return getViewContext().cloneActionURL().setAction("showList").deleteParameter("fileNames");
        }

        public boolean doAction(SqlScriptForm form, BindException errors) throws Exception
        {
            List<SqlScript> scripts = getScripts(form);

            for (SqlScriptRunner.SqlScript script : scripts)
                SqlScriptManager.insert(getViewContext().getUser(), script);

            return true;
        }

        public void validateCommand(SqlScriptForm target, Errors errors)
        {
        }

        protected abstract List<SqlScript> getScripts(SqlScriptForm form) throws SQLException;
    }


    @RequiresSiteAdmin
    public class InsertAllAction extends InsertScriptsAction
    {
        protected List<SqlScript> getScripts(SqlScriptForm form) throws SQLException
        {
            return SqlScriptRunner.getNewScripts(form.getProvider(), form.getSchemaName());
        }
    }


    @RequiresSiteAdmin
    public class InsertSelectedAction extends InsertScriptsAction
    {
        protected List<SqlScript> getScripts(SqlScriptForm form) throws SQLException
        {
            return getPostedScripts(form.getProvider());
        }
    }


    @RequiresSiteAdmin
    public class ShowScriptAction extends SimpleViewAction<SqlScriptForm>
    {
        public ModelAndView getView(SqlScriptForm form, BindException errors) throws Exception
        {
            SqlScriptRunner.SqlScript script = form.getProvider().getScript(form.getFileName());
            return new ScriptView(script);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class ScriptView extends HttpView
    {
        private SqlScriptRunner.SqlScript _script = null;

        protected ScriptView(SqlScriptRunner.SqlScript script)
        {
            _script = script;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            boolean exists = SqlScriptManager.hasBeenRun(_script);

            out.println("<html>\n<head><title>" + _script.getDescription() + "</title></head><body><pre>");
            out.print("<b>");

            if (exists)
                out.print("<font class=\"labkey-error\">Note: This script has already been run</font>\n\n");

            out.print(_script.getDescription());
            out.print("</b>\n\n");
            out.print(PageFlowUtil.filter(_script.getContents()));
            String error = _script.getErrorMessage();
            if (null != error)
                out.print(error);
            out.print("</pre>\n");

            ButtonBar bb = new ButtonBar();

            ActionURL cloneUrl = getViewContext().cloneActionURL();
            String fileName = cloneUrl.getParameter("fileName");
            cloneUrl.deleteParameter("fileName");

            ActionButton showList = new ActionButton(cloneUrl.setAction("showList").getEncodedLocalURIString(), "Show List");
            showList.setActionType(ActionButton.Action.LINK);
            bb.add(showList);

            cloneUrl.addParameter("fileNames", fileName);
            ActionButton runScript = new ActionButton(cloneUrl.clone().setAction("runSelected").replaceParameter("allowMultipleSubmits", "1").getEncodedLocalURIString(), "Run Script" + (exists ? " Again" : ""));
            runScript.setActionType(ActionButton.Action.LINK);
            bb.add(runScript);

            if (!exists)
            {
                ActionButton markScript = new ActionButton(cloneUrl.setAction("insertSelected").getEncodedLocalURIString(), "Mark Script As \"Run\"");
                markScript.setActionType(ActionButton.Action.LINK);
                bb.add(markScript);
            }

            bb.render(new RenderContext(getViewContext()), out);
            out.print("\n</body>\n</html>");
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
