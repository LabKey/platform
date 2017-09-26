/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
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
    private static final List<TabProvider> REGISTERED_TAB_PROVIDERS = new CopyOnWriteArrayList<>();

    /**
     * Register a tab that can appear on the folder management page. Implement a FolderManagementAction (subclass
     * FolderManagementViewAction or FolderManagementViewPostAction) then add it to the tabs using this method.
     *
     * @param text Tab caption
     * @param id Unique string that identifies the tab. Used by the tab strip to highlight the selected tab.
     * @param filter A predicate that determines if the tab should be rendered in this container.
     * @param actionClass Your FolderManagementAction class. TabProvider will create the tab's ActionURL from this class, the current container, and the tabId.
     */
    public static void addTab(String text, String id, Predicate<Container> filter, Class<? extends FolderManagementAction> actionClass)
    {
        REGISTERED_TAB_PROVIDERS.add(new TabProvider(text, id, filter, actionClass));
    }

    public static final Predicate<Container> EVERY_CONTAINER = container -> true;
    public static final Predicate<Container> NOT_ROOT = container -> !container.isRoot();
    public static final Predicate<Container> FOLDERS_AND_PROJECTS = container -> !container.isRoot() && !container.isWorkbook();
    public static final Predicate<Container> FOLDERS_ONLY = container -> !container.isRoot() && !container.isProject() && !container.isWorkbook();

    public abstract static class FolderManagementTabStrip extends TabStripView
    {
        private final Container _container;
        private final BindException _errors;

        private FolderManagementTabStrip(Container c, String tabId, BindException errors)
        {
            _container = c;
            _errors = errors;

            // Stay on same tab if there are errors
            if (_errors.hasErrors() && null != StringUtils.trimToNull(tabId))
                setSelectedTabId(tabId);
        }

        public List<NavTree> getTabList()
        {
            return FolderManagement.REGISTERED_TAB_PROVIDERS.stream()
                .filter(provider->provider.shouldRender(_container))
                .map(provider->provider.getTab(_container))
                .collect(Collectors.toList());
        }

        public abstract HttpView getTabView(String tabId) throws Exception;
    }


    /**
     * Marker interface for actions that register themselves with the folder management page
     */
    public interface FolderManagementAction extends Controller
    {
    }


    /**
     * Base action class for folder management actions that only need to display a view (no post handling).
     */
    public static abstract class FolderManagementViewAction extends SimpleViewAction<Void> implements FolderManagementAction
    {
        @Override
        public ModelAndView getView(Void form, BindException errors) throws Exception
        {
            return new FolderManagementTabStrip(getContainer(), (String)getViewContext().get("tabId"), errors)
            {
                @Override
                public HttpView getTabView(String tabId) throws Exception
                {
                    return FolderManagementViewAction.this.getTabView();
                }
            };
        }

        protected abstract HttpView getTabView() throws Exception;

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return folderManagementNavTrail(root, getContainer(), getUser());
        }
    }


    /**
     * Base action class for folder management actions that display a view and handle a post.
     *
     * @param <FORM> Bean form used by your post handler
     */
    public static abstract class FolderManagementViewPostAction<FORM> extends FormViewAction<FORM> implements FolderManagementAction
    {
        @Override
        public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            return new FolderManagementTabStrip(getContainer(), (String)getViewContext().get("tabId"), errors)
            {
                @Override
                public HttpView getTabView(String tabId) throws Exception
                {
                    return FolderManagementViewPostAction.this.getTabView(form, errors);
                }
            };
        }

        @Override
        public URLHelper getSuccessURL(FORM form)
        {
            return null;
        }

        protected abstract HttpView getTabView(FORM form, BindException errors) throws Exception;

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return folderManagementNavTrail(root, getContainer(), getUser());
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
        private final Class<? extends FolderManagementAction> _actionClass;

        private TabProvider(String text, String id, Predicate<Container> filter, Class<? extends FolderManagementAction> actionClass)
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
