/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Responsible for injecting the right set of links in the Admin menu that's part of the page header for users
 * with sufficient permissions.
 * User: adam
 * Date: 1/10/2015
 */
public class AdminLinkManager
{
    private static final AdminLinkManager INSTANCE = new AdminLinkManager();
    private final List<Listener> _listeners = new CopyOnWriteArrayList<>();

    public static AdminLinkManager getInstance()
    {
        return INSTANCE;
    }

    private AdminLinkManager()
    {
    }

    public void addListener(Listener listener)
    {
        _listeners.add(listener);
    }

    public void addStandardAdminLinks(NavTree adminNavTree, Container container, User user)
    {
        for (Listener listener : _listeners)
            listener.addAdminLinks(adminNavTree, container, user);
    }

    /**
     * Modules implement and register this interface to add module-specific links to the admin menu, regardless of the
     * current folder type. Override FolderType.addManageLinks() to add links only when a specific folder type is in use.
     */
    public interface Listener
    {
        /**
         * Add module-specific links to the admin popup menu. Implementors must ensure that user has the required permissions
         * in the container before adding links. User might not be an administrator in this container (could be a troubleshooter,
         * for example).
         */
        public void addAdminLinks(NavTree adminNavTree, Container container, User user);
    }
}
