/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

/**
 * User: kevink
 * Date: 2/21/14
 */
@Marshal(Marshaller.Jackson)
public class LoggerController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(LoggerController.class);

    private static final Logger LOG = LogManager.getLogger(LoggerController.class);

    public static void registerAdminConsoleLinks()
    {
        Container root = ContainerManager.getRoot();

        AdminConsole.addLink(AdminConsole.SettingsLinkType.Diagnostics, "loggers", new ActionURL(ManageAction.class, root), AdminOperationsPermission.class);
    }

    public LoggerController()
    {
        setActionResolver(_actionResolver);
    }

    public static class LoggerLevel
    {
        private String name;
        private String parent;
        private String level;
        private boolean inherited;

        public static LoggerLevel fromLogger(LoggerConfig log)
        {
            LoggerConfig parent = log.getParent();
            Level level = log.getLevel();
            Level effectiveLevel = log.getLevel();

            LoggerLevel loggerLevel = new LoggerLevel();
            loggerLevel.setName(log.getName());
            loggerLevel.setParent(parent != null ? parent.getName() : null);
            loggerLevel.setLevel(level != null ? level.toString() : effectiveLevel.toString());
            loggerLevel.setInherited(level == null);
            return loggerLevel;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getParent()
        {
            return parent;
        }

        public void setParent(String parent)
        {
            this.parent = parent;
        }

        public String getLevel()
        {
            return level;
        }

        public void setLevel(String level)
        {
            this.level = level;
        }

        public boolean isInherited()
        {
            return inherited;
        }

        public void setInherited(boolean inherited)
        {
            this.inherited = inherited;
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
    public class ListAction extends ReadOnlyApiAction<ListFilter>
    {
        @Override
        public SimpleResponse<Collection<LoggerLevel>> execute(ListFilter filter, BindException errors)
        {
            Level filterLevel = filter.getLevel() != null ? Level.toLevel(filter.getLevel()) : null;

            Collection<LoggerLevel> loggers = new ArrayList<>();
            LoggerContext loggerContext = (LoggerContext) LogManager.getContext(true);
            Configuration configuration = loggerContext.getConfiguration();


            Collection<LoggerConfig> currentLoggers = configuration.getLoggers().values();
            for (LoggerConfig currentLogger: currentLoggers)
            {
                if (!filter.isInherited() && currentLogger.getLevel() == null)
                    continue;

                if (filterLevel != null && !(filterLevel.equals(currentLogger.getLevel())))
                    continue;

                if (filter.getContains() != null && !StringUtils.containsIgnoreCase(currentLogger.getName(), filter.getContains()))
                    continue;

                loggers.add(LoggerLevel.fromLogger(currentLogger));
            }

            return success(loggers);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ResetAction extends MutatingApiAction<Object>
    {
        @Override
        public SimpleResponse execute(Object o, BindException errors)
        {
            LoggerContext.getContext(true).setConfiguration(new NullConfiguration());
            URL url = getClass().getResource("/log4j.xml");
            // TODO : log4j
//            DOMConfigurator.configure(url);
            return success();
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class UpdateAction extends MutatingApiAction<LoggerLevel>
    {
        @Override
        public SimpleResponse<LoggerLevel> execute(LoggerLevel loggerLevel, BindException errors)
        {
            Logger logger = LogManager.getLogger(loggerLevel.name);
            if (logger == null)
                throw new NotFoundException("logger");

            // Update the logger level
            if (loggerLevel.level != null && (logger.getLevel() == null || !loggerLevel.level.equals(logger.getLevel().toString())))
            {
                Configurator.setLevel(logger.getName(), Level.toLevel(loggerLevel.level));
            }

            LoggerContext loggerContext = (LoggerContext) LogManager.getContext(true);
            Configuration configuration = loggerContext.getConfiguration();

            return success(LoggerLevel.fromLogger(configuration.getLoggerConfig(logger.getName())));
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
        public ModelAndView getView(Object o, boolean reshow, BindException errors)
        {
            getPageConfig().setTitle("Manage Log4J Loggers");
            return new JspView<>("/org/labkey/core/admin/logger/manage.jsp", null, errors);
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            return false;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

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
