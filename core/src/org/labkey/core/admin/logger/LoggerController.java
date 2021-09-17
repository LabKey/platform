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
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleResponse;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
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
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
        private String notes;

        public static LoggerLevel fromLoggerConfig(LoggerConfig log)
        {
            LoggerConfig parent = log.getParent();
            String parentName = parent != null ? parent.getName() : "";

            return getLoggerLevel(parentName, log.getLevel(), log.getName());
        }

        public static LoggerLevel fromLogger(Logger log)
        {
            Logger parent = ((org.apache.logging.log4j.core.Logger) log).getParent();
            String parentName = parent != null ? parent.getName() : null;

            return getLoggerLevel(parentName, log.getLevel(), log.getName());
        }

        private static LoggerLevel getLoggerLevel(String parent, Level level, String name)
        {
            LoggerLevel loggerLevel = new LoggerLevel();
            loggerLevel.setName(name);
            loggerLevel.setParent(parent);
            loggerLevel.setLevel(level.toString());
            boolean inherited = parent != null && !parent.equalsIgnoreCase(name);
            loggerLevel.setInherited(inherited);
            loggerLevel.setNotes(LogHelper.getNote(name));
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

        public String getNotes()
        {
            return notes;
        }

        public void setNotes(String notes)
        {
            this.notes = notes;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LoggerLevel that = (LoggerLevel) o;
            return inherited == that.inherited &&
                    name.equals(that.name) &&
                    Objects.equals(parent, that.parent) &&
                    Objects.equals(notes, that.notes) &&
                    level.equals(that.level);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(name, parent, level, inherited, notes);
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
            Set<LoggerLevel> loggers = new HashSet<>();
            LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = loggerContext.getConfiguration();

            Collection<org.apache.logging.log4j.core.Logger> currentLoggers = loggerContext.getLoggers();
            Collection<LoggerConfig> loggerConfigs = configuration.getLoggers().values();

            for (LoggerConfig currentLogger: loggerConfigs)
            {
                if (filterCheck(filter, currentLogger.getLevel(), currentLogger.getName()))
                    continue;

                loggers.add(LoggerLevel.fromLoggerConfig(currentLogger));
            }

            for (Logger currentLogger: currentLoggers)
            {
                if (filterCheck(filter, currentLogger.getLevel(), currentLogger.getName()))
                    continue;

                loggers.add(LoggerLevel.fromLogger(currentLogger));
            }

            return success(loggers);
        }

        private boolean filterCheck(ListFilter filter, Level level, String loggerName)
        {
            Level filterLevel = filter.getLevel() != null ? Level.toLevel(filter.getLevel()) : null;

            if (!filter.isInherited() && level == null)
                return true;

            if (filterLevel != null && !(filterLevel.equals(level)))
                return true;

            return filter.getContains() != null && !StringUtils.containsIgnoreCase(loggerName, filter.getContains());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ResetAction extends MutatingApiAction<Object>
    {
        @Override
        public SimpleResponse execute(Object o, BindException errors) throws URISyntaxException
        {
            ((org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false)).reconfigure();
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
        public ModelAndView getView(Object o, boolean reshow, BindException errors)
        {
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
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "Manage Log4J Loggers", getClass(), getContainer());
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
