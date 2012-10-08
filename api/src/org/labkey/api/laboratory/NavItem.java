package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 8:44 AM
 */

/**
 * Experimental.  This describes
 */
public interface NavItem
{
    public static final String PROPERTY_CATEGORY = "laboratory.navItem";

    public DataProvider getDataProvider();

    public String getName();

    public String getLabel();

    public String getCategory();

    public String getRendererName();

    public boolean isImportIntoWorkbooks();

    public boolean isVisible(Container c, User u);

    public boolean getDefaultVisibility(Container c, User u);

    public ActionURL getImportUrl(Container c, User u);

    public ActionURL getSearchUrl(Container c, User u);

    public ActionURL getBrowseUrl(Container c, User u);

    public ActionURL getPrepareExptUrl(Container c, User u);

    public JSONObject toJSON(Container c, User u);

    public String getPropertyManagerKey();

    public static enum Category
    {
        samples(),
        misc(),
        settings(),
        report(),
        data();

        Category()
        {

        }
    }
}
