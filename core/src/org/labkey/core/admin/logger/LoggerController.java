/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.core.admin.logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.SimpleResponse;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

/**
 * User: kevink
 * Date: 2/21/14
 */
@Marshal(Marshaller.Jackson)
public class LoggerController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(
            LoggerController.class);

    private static final Logger LOG = Logger.getLogger(LoggerController.class);

    public static void registerAdminConsoleLinks()
    {
        Container root = ContainerManager.getRoot();

        AdminConsole.addLink(AdminConsole.SettingsLinkType.Diagnostics, "loggers", new ActionURL(ManageAction.class, root), AdminOperationsPermission.class);
    }

    public LoggerController()
    {
        setActionResolver(_actionResolver);
    }

    static class LoggerLevel
    {
        public final String name;
        public final String parent;
        public final String level;
        public final boolean inherited;

        public LoggerLevel(String name, String parent, String level, boolean inherited)
        {
            this.name = name;
            this.parent = parent;
            this.level = level;
            this.inherited = inherited;
        }

        @JsonCreator
        public static LoggerLevel fromJson(@JsonProperty("name") String name, @JsonProperty("level") String level)
        {
            return new LoggerLevel(name, null, level, false);
        }

        public static LoggerLevel fromLogger(Logger log)
        {
            Category parent = log.getParent();
            Level level = log.getLevel();
            Level effectiveLevel = log.getEffectiveLevel();

            return new LoggerLevel(log.getName(),
                    parent != null ? parent.getName() : null,
                    level != null ? level.toString() : effectiveLevel.toString(),
                    level == null);
        }
    }

    public static class ListFilter
    {
        private boolean _inherited = true; // include inherited Loggers by default
        private String _contains;
        private String _level;

        public void setInherited(boolean inherited)
        {
            _inherited = inherited;
        }

        public boolean isInherited()
        {
            return _inherited;
        }

        public void setContains(String contains)
        {
            _contains = contains;
        }

        public String getContains()
        {
            return _contains;
        }

        public String getLevel()
        {
            return _level;
        }

        public void setLevel(String level)
        {
            _level = level;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ListAction extends ApiAction<ListFilter>
    {
        @Override
        public SimpleResponse<Collection<LoggerLevel>> execute(ListFilter filter, BindException errors) throws Exception
        {
            Level filterLevel = filter.getLevel() != null ? Level.toLevel(filter.getLevel()) : null;

            Collection<LoggerLevel> loggers = new ArrayList<>();
            Enumeration<Logger> currentLoggers = (Enumeration<Logger>) LogManager.getCurrentLoggers();
            while (currentLoggers.hasMoreElements())
            {
                Logger log = currentLoggers.nextElement();
                if (!filter.isInherited() && log.getLevel() == null)
                    continue;

                if (filterLevel != null && !(filterLevel.equals(log.getLevel()) || filterLevel.equals(log.getEffectiveLevel())))
                    continue;

                if (filter.getContains() != null && !StringUtils.containsIgnoreCase(log.getName(), filter.getContains()))
                    continue;

                loggers.add(LoggerLevel.fromLogger(log));
            }

            return success(loggers);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ResetAction extends ApiAction<Object>
    {
        @Override
        public SimpleResponse execute(Object o, BindException errors) throws Exception
        {
            LogManager.resetConfiguration();
            URL url = getClass().getResource("/log4j.xml");
            DOMConfigurator.configure(url);
            return success();
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class UpdateAction extends ApiAction<LoggerLevel>
    {
        @Override
        protected ModelAndView handleGet() throws Exception
        {
            getViewContext().getResponse().sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "You must use the POST method when calling this action.");
            return null;
        }

        @Override
        public SimpleResponse<LoggerLevel> execute(LoggerLevel loggerLevel, BindException errors) throws Exception
        {
            Logger logger = LogManager.getLogger(loggerLevel.name);
            if (logger == null)
                throw new NotFoundException("logger");

            // Update the logger level
            if (loggerLevel.level != null && (logger.getLevel() == null || !loggerLevel.level.equals(logger.getLevel().toString())))
            {
                logger.setLevel(Level.toLevel(loggerLevel.level));
            }

            return success(LoggerLevel.fromLogger(logger));
        }
    }


    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
    public class ManageAction extends FormViewAction<Object>
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/logger/manage.jsp", null, errors);
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            return false;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.isInSiteAdminGroup());

            LoggerController controller = new LoggerController();

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                controller.new ListAction(),
                controller.new ResetAction(),
                controller.new UpdateAction()
            );

            // @AdminConsoleAction
            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(ContainerManager.getRoot(), user,
                controller.new ManageAction()
            );
        }
    }
}
