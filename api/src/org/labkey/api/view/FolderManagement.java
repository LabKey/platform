/*
 * Copyright (c) 2011-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * User: klum
 * Date: Jan 17, 2011
 */
public class FolderManagement
{
    public enum TYPE
    {
        FolderManagement
        {
            private final List<TabProvider> FOLDER_TAB_PROVIDERS = new CopyOnWriteArrayList<>();

            @Override
            void validateContainer(Container c)
            {
                // Allow all containers for now
            }

            @Override
            List<TabProvider> getTabProviders()
            {
                return FOLDER_TAB_PROVIDERS;
            }

            @Override
            NavTree appendNavTrail(BaseViewAction action, NavTree root, Container c, User user)
            {
                return folderManagementNavTrail(root, c, user);
            }
        },
        ProjectSettings
        {
            private final List<TabProvider> PROJECT_TAB_PROVIDERS = new CopyOnWriteArrayList<>();

            @Override
            void validateContainer(Container c)
            {
                if (!c.isRoot() && !c.isProject())
                    throw new NotFoundException("Valid only from root or project");
            }

            @Override
            List<TabProvider> getTabProviders()
            {
                return PROJECT_TAB_PROVIDERS;
            }

            @Override
            NavTree appendNavTrail(BaseViewAction action, NavTree root, Container c, User user)
            {
                action.setHelpTopic(new HelpTopic("customizeLook"));

                if (c.isRoot())
                    root.addChild("Admin Console", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()).addChild("Look and Feel Settings");
                else
                    root.addChild("Project Settings");
                return root;
            }
        };

        abstract void validateContainer(Container c);
        abstract List<TabProvider> getTabProviders();
        abstract NavTree appendNavTrail(BaseViewAction action, NavTree root, Container c, User user);
    }

    @Deprecated // Left behind for backward compatibility  TODO: Migrate callers and delete this!
    public static void addTab(String text, String id, Predicate<Container> filter, Class<? extends ManagementAction> actionClass)
    {
        addTab(TYPE.FolderManagement, text, id, filter, actionClass);
    }

    /**
     * Register a tab that can appear on the folder management or project settings page. Implement a ManagementAction
     * (by subclassing FolderManagementViewAction, FolderManagementViewPostAction, ProjectSettingsViewAction, or
     * ProjectSettingsViewPostAction) and then add it to the tabs using this method.
     *
     * @param type Management page type
     * @param text Tab caption
     * @param id Unique string that identifies the tab. Used by the tab strip to highlight the selected tab.
     * @param filter A predicate that determines if the tab should be rendered in this container.
     * @param actionClass Your ManagementAction class. TabProvider will create the tab's ActionURL from this class, the current container, and the tabId.
     */
    public static void addTab(TYPE type, String text, String id, Predicate<Container> filter, Class<? extends ManagementAction> actionClass)
    {
        type.getTabProviders().add(new TabProvider(text, id, filter, actionClass));
    }

    public static final Predicate<Container> EVERY_CONTAINER = container -> true;
    public static final Predicate<Container> NOT_ROOT = container -> !container.isRoot();
    public static final Predicate<Container> ROOT_AND_PROJECTS = container -> container.isRoot() || container.isProject();
    // Choosing "isInFolderNav" does not include Tabs, which is a slight change in functionality (previously excluded just workbooks), but that seems proper.
    public static final Predicate<Container> FOLDERS_AND_PROJECTS = container -> !container.isRoot() && container.isInFolderNav();
    public static final Predicate<Container> FOLDERS_ONLY = container -> !container.isRoot() && !container.isProject() && container.isInFolderNav();
    public static final Predicate<Container> PROJECTS_ONLY = container -> !container.isRoot() && container.isProject() && container.isInFolderNav();

    private abstract static class ManagementTabStrip extends TabStripView
    {
        private final Container _container;

        ManagementTabStrip(Container c, String tabId, BindException errors)
        {
            _container = c;

            // Stay on same tab if there are errors
            if (errors.hasErrors() && null != StringUtils.trimToNull(tabId))
                setSelectedTabId(tabId);
        }

        public List<NavTree> getTabList()
        {
            return getTabProviders().stream()
                .filter(provider->provider.shouldRender(_container))
                .map(provider->provider.getTab(_container))
                .collect(Collectors.toList());
        }

        protected abstract List<TabProvider> getTabProviders();
        public abstract HttpView getTabView(String tabId) throws Exception;
    }


    /**
     * Marker interface for actions that register themselves with a management page
     */
    interface ManagementAction extends Controller
    {
    }


    /**
     * Base action class for management actions that only need to display a view (no post handling).
     */
    private static abstract class ManagementViewAction extends SimpleViewAction<Void> implements ManagementAction
    {
        @Override
        public ModelAndView getView(Void form, BindException errors)
        {
            getType().validateContainer(getContainer());

            return new ManagementTabStrip(getContainer(), (String)getViewContext().get("tabId"), errors)
            {
                @Override
                public HttpView getTabView(String tabId) throws Exception
                {
                    return ManagementViewAction.this.getTabView();
                }

                @Override
                protected List<TabProvider> getTabProviders()
                {
                    return getType().getTabProviders();
                }
            };
        }

        protected abstract TYPE getType();
        protected abstract HttpView getTabView() throws Exception;

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return getType().appendNavTrail(this, root, getContainer(), getUser());
        }
    }


    /**
     * Base action class for management actions that display a view and handle a post.
     */
    private static abstract class ManagementViewPostAction<FORM> extends FormViewAction<FORM> implements ManagementAction
    {
        @Override
        public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            getType().validateContainer(getContainer());

            return new ManagementTabStrip(getContainer(), (String)getViewContext().get("tabId"), errors)
            {
                @Override
                public HttpView getTabView(String tabId) throws Exception
                {
                    return ManagementViewPostAction.this.getTabView(form, reshow, errors);
                }

                @Override
                protected List<TabProvider> getTabProviders()
                {
                    return getType().getTabProviders();
                }
            };
        }

        // TODO: Validate container in postHandler()

        @Override
        public URLHelper getSuccessURL(FORM form)
        {
            return null;
        }

        protected abstract TYPE getType();

        // TODO: Make this abstract, once every subclass overrides this method
        protected HttpView getTabView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            return getTabView(form, errors);
        }

        @Deprecated // Remove this in favor of the "reshow" variant above...
        protected HttpView getTabView(FORM form, BindException errors) throws Exception
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return getType().appendNavTrail(this, root, getContainer(), getUser());
        }
    }


    /**
     * Base action class for folder management actions that only need to display a view (no post handling).
     */
    public static abstract class FolderManagementViewAction extends ManagementViewAction
    {
        @Override
        final protected TYPE getType()
        {
            return TYPE.FolderManagement;
        }
    }


    /**
     * Base action class for project settings actions that only need to display a view (no post handling).
     */
    public static abstract class ProjectSettingsViewAction extends ManagementViewAction
    {
        @Override
        final protected TYPE getType()
        {
            return TYPE.ProjectSettings;
        }
    }


    /**
     * Base action class for folder management actions that display a view and handle a post.
     *
     * @param <FORM> Bean form used by your post handler
     */
    public static abstract class FolderManagementViewPostAction<FORM> extends ManagementViewPostAction<FORM>
    {
        @Override
        final protected TYPE getType()
        {
            return TYPE.FolderManagement;
        }
    }


    /**
     * Base action class for project settings actions that display a view and handle a post.
     *
     * @param <FORM> Bean form used by your post handler
     */
    public static abstract class ProjectSettingsViewPostAction<FORM> extends ManagementViewPostAction<FORM>
    {
        @Override
        final protected TYPE getType()
        {
            return TYPE.ProjectSettings;
        }
    }


    private static NavTree folderManagementNavTrail(NavTree root, Container c, User user)
    {
        if (c.isRoot())
            return PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Admin Console", null);

        if (c.isContainerTab())
            root.addChild(c.getParent().getName(), c.getParent().getStartURL(user));

        root.addChild(c.getName(), c.getStartURL(user));
        root.addChild("Folder Management");

        return root;
    }


    private static class TabProvider
    {
        private final String _text;
        private final String _id;
        private final Predicate<Container> _filter;
        private final Class<? extends ManagementAction> _actionClass;

        private TabProvider(String text, String id, Predicate<Container> filter, Class<? extends ManagementAction> actionClass)
        {
            _text = text;
            _id = id;
            _actionClass = actionClass;
            _filter = filter;
        }

        private ActionURL getActionURL(Container c)
        {
            ActionURL url = new ActionURL(_actionClass, c);
            url.addParameter("tabId", _id);

            return url;
        }

        private boolean shouldRender(Container c)
        {
            return _filter.test(c);
        }

        private NavTree getTab(Container c)
        {
            NavTree navTree = new NavTree(_text, getActionURL(c));
            navTree.setId(_id);

            return navTree;
        }
    }
}
