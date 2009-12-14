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

package org.labkey.cabig;

import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.*;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * User: adam
 * Date: Nov 22, 2007
 * Time: 8:32:19 AM
 */
public class caBIGController extends SpringActionController
{
    private static ActionResolver _actionResolver = new DefaultActionResolver(caBIGController.class);

    public caBIGController()
    {
        setActionResolver(_actionResolver);
    }


    public static ActionURL getCaBigURL(Class<? extends Controller> actionClass, Container c, ActionURL returnURL)
    {
        ActionURL url = new ActionURL(actionClass, c);
        return url.addReturnURL(returnURL);
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(getCaBigURL(AdminAction.class, getContainer(), PageFlowUtil.urlProvider(SecurityUrls.class).getContainerURL(getContainer())));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public abstract class AbstractPublishAction extends RedirectAction<ReturnUrlForm>
    {
        public void validateCommand(ReturnUrlForm target, Errors errors)
        {
        }

        public final boolean doAction(ReturnUrlForm form, BindException errors) throws Exception
        {
            doAction();
            return true;
        }

        protected abstract void doAction() throws Exception;

        public ActionURL getSuccessURL(ReturnUrlForm form)
        {
            return form.getReturnActionURL();
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class PublishAction extends AbstractPublishAction
    {
        protected void doAction() throws Exception
        {
            caBIGManager.get().publish(getContainer());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class UnpublishAction extends AbstractPublishAction
    {
        protected void doAction() throws Exception
        {
            caBIGManager.get().unpublish(getContainer());
        }
    }



    @RequiresPermissionClass(AdminPermission.class)
    public class PublishAllAction extends AbstractPublishAction
    {
        protected void doAction() throws Exception
        {
            setPublishState(State.Publish);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class UnpublishAllAction extends AbstractPublishAction
    {
        protected void doAction() throws Exception
        {
            setPublishState(State.Unpublish);
        }
    }


    private enum State
    {
        Publish
        {
            void setState(Container c) throws SQLException
            {
                caBIGManager.get().publish(c);
            }
        },
        Unpublish
        {
            void setState(Container c) throws SQLException
            {
                caBIGManager.get().unpublish(c);
            }
        };

        abstract void setState(Container c) throws SQLException;
    }


    private void setPublishState(State state) throws Exception
    {
        List<String> containerIds = getViewContext().getList("containerIds");

        for (String id : containerIds)
        {
            Container c = ContainerManager.getForId(id);
            if (!c.hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException();

            state.setState(c);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class AdminAction extends SimpleViewAction<ReturnUrlForm>
    {
        public ModelAndView getView(ReturnUrlForm form, BindException errors) throws Exception
        {
            ActionURL currentUrl = getViewContext().getActionURL();
            caBIGHierarchyTree tree = new caBIGHierarchyTree(getContainer().getPath(), getUser(), ACL.PERM_ADMIN, currentUrl);

            StringBuilder html = new StringBuilder();
            html.append("<script type=\"text/javascript\">\n").append("LABKEY.requiresScript('DataRegion.js');\n").append("</script>");
            html.append("Click the buttons below to publish or unpublish folders to the caBIG&trade; interface.  If your caBIG&trade; web application ");
            html.append("is running then all experiment data in published folders is publicly visible via the caBIG&trade; interface.<br><br>");
            html.append("For more information about publishing to caBIG&trade;, ");
            html.append("<a href=\"").append(PageFlowUtil.filter(new HelpTopic("cabig", HelpTopic.Area.CPAS).getHelpTopicLink())).append("\">click here</a>.<br><br>\n");
            html.append("<form method=post action=''>");
            html.append("<input type=\"hidden\" name=\"returnUrl\" value=\"");
            html.append(currentUrl.getEncodedLocalURIString());
            html.append("\"><table class=\"labkey-data-region\">");
            tree.render(html);
            html.append("</table><br>");
            renderHierarchyButtonBar(html, form.getReturnActionURL());
            html.append("</form>");

            return new HtmlView(html.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    private void renderHierarchyButtonBar(StringBuilder html, ActionURL returnUrl) throws IOException, ServletException
    {
        ButtonBar bb = new ButtonBar();

        ActionButton publishAll = new ActionButton("publishAll.post", "Publish All");
        publishAll.setActionType(ActionButton.Action.POST);
        bb.add(publishAll);

        ActionButton unpublishAll = new ActionButton("unpublishAll.post", "Unpublish All");
        unpublishAll.setActionType(ActionButton.Action.POST);
        bb.add(unpublishAll);

        ActionButton done = new ActionButton("Done", returnUrl);
        bb.add(done);

        StringWriter s = new StringWriter();
        bb.render(new RenderContext(getViewContext()), s);
        html.append(s);
    }


    private static class caBIGPermissionsView extends JspView
    {
        private caBIGPermissionsView()
        {
            super("/org/labkey/cabig/view/publish.jsp");
            setTitle("Publish to caBIG");
        }
    }


    public static class caBIGPermissionsViewFactory implements org.labkey.api.security.SecurityManager.ViewFactory
    {
        public HttpView createView(ViewContext context)
        {
            if (AppProps.getInstance().isCaBIGEnabled())
                return new caBIGPermissionsView();
            else
                return null;
        }
    }


    private static class caBIGHierarchyTree extends ContainerTree
    {
        private static caBIGManager _caBIG = caBIGManager.get();
        private static String _unauthorizedButton = PageFlowUtil.generateDisabledButton("Not Authorized");
        private ActionURL _currentUrl;

        private caBIGHierarchyTree(String rootPath, User user, int perm, ActionURL currentUrl)
        {
            super(rootPath, user, perm, currentUrl);
            _currentUrl = currentUrl;
        }


        @Override
        protected void renderNodeStart(StringBuilder html, Container c, ActionURL url, boolean isAuthorized, int level)
        {
            html.append("<tr><td>");
            appendButton(html, c, isAuthorized, _currentUrl);
            appendContainerId(html, c, isAuthorized);
            html.append("</td><td style=\"padding-left:");
            html.append(10 * level);
            html.append("\">");
        }


        private static void appendButton(StringBuilder html, Container c, boolean isAuthorized, ActionURL returnURL)
        {
            if (!isAuthorized)
            {
                html.append(_unauthorizedButton);
            }
            else
            {
                boolean isPublished = isPublished(c);
                ActionURL publishURL = getCaBigURL(isPublished ? UnpublishAction.class : PublishAction.class, c, returnURL);
                html.append(PageFlowUtil.generateButton(isPublished ? "Unpublish" : "Publish", publishURL));
            }
        }


        private static void appendContainerId(StringBuilder html, Container c, boolean isAuthorized)
        {
            if (isAuthorized)
                html.append("<input type=\"hidden\" name=\"containerIds\" value=\"").append(c.getId()).append("\">");
        }


        private static boolean isPublished(Container c)
        {
            try
            {
                return _caBIG.isPublished(c);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }
}
