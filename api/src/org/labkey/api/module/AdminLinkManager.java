package org.labkey.api.module;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: adam
 * Date: 1/10/2015
 * Time: 11:38 AM
 */
public class AdminLinkManager
{
    private static final AdminLinkManager INSTANCE = new AdminLinkManager();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public static AdminLinkManager getInstance()
    {
        return INSTANCE;
    }

    private AdminLinkManager()
    {
    }

    public void addListener(Listener listener)
    {
        LISTENERS.add(listener);
    }

    public void addStandardAdminLinks(NavTree adminNavTree, Container container, User user)
    {
        for (Listener listener : LISTENERS)
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
