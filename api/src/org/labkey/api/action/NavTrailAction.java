package org.labkey.api.action;

import org.labkey.api.view.NavTree;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 21, 2007
 * Time: 8:05:32 PM
 */
public interface NavTrailAction
{
    /** NOTE: this works a little different than NavTrailConfig!
     *
     * Return the entire nav trail for this page, including the entry
     * representing this page/action with URL.
     *
     * Base class implementations may use the entry as the page title by default.
     *
     * @returns Return the tree handed in for convienence
     *
     * @param root
     * @return
     */
    NavTree appendNavTrail(NavTree root);
}