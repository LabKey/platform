package org.labkey.mothership.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.MenuButton;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.mothership.MothershipController;
import org.labkey.mothership.MothershipManager;
import org.springframework.validation.BindException;

/**
 * User: jeckels
 * Date: Feb 23, 2012
 */
public class ExceptionStackTraceQueryView extends QueryView
{
    public ExceptionStackTraceQueryView(MothershipSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
    {
        super.populateButtonBar(view, bar, exportAsWebPage);

        MenuButton assignToButton = new MenuButton("Assign To");
        assignToButton.setRequiresSelection(true);
        assignToButton.setDisplayPermission(UpdatePermission.class);
        for (User user : MothershipManager.get().getAssignedToList(getSchema().getContainer()))
        {
            ActionURL url = new ActionURL(MothershipController.BulkUpdateAction.class, getContainer());
            url.addParameter("userId", user.getUserId());
            String script = "if (verifySelected(document.forms['" + getDataRegionName() + "'], '" + url +
            "', 'post', 'rows')) document.forms['" + getDataRegionName() + "'].submit();";

            assignToButton.addMenuItem(user.getDisplayName(getSchema().getUser()), null, script);
        }
        assignToButton.addSeparator();
        ActionURL ignoreURL = new ActionURL(MothershipController.BulkUpdateAction.class, getContainer());
        ignoreURL.addParameter("ignore", true);
        String ignoreScript = "if (verifySelected(document.forms['" + getDataRegionName() + "'], '" + ignoreURL +
        "', 'post', 'rows')) document.forms['" + getDataRegionName() + "'].submit();";
        assignToButton.addMenuItem("Ignore", null, ignoreScript);
        bar.add(assignToButton);
    }
}
