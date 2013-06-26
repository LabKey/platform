package org.labkey.api.wiki;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: kevink
 * Date: 6/26/13
 */
public interface WikiChangeListener
{
    /**
     * Called when a wiki is created.
     *
     * @param user The user that initiated the change.
     * @param c The container the wiki is saved in.
     * @param name The name of the wiki.
     */
    void wikiCreated(User user, Container c, String name);

    /**
     * Called when a wiki is updated.
     *
     * @param user The user that initiated the change.
     * @param c The container the wiki is saved in.
     * @param name The name of the wiki.
     */
    void wikiChanged(User user, Container c, String name);

    /**
     * Called when a wiki is deleted.
     *
     * @param user The user that initiated the change.
     * @param c The container the wiki is saved in.
     * @param name The name of the wiki.
     */
    void wikiDeleted(User user, Container c, String name);
}
