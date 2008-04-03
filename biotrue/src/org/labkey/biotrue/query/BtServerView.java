package org.labkey.biotrue.query;

import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ActionButton;
import org.labkey.api.security.ACL;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Apr 3, 2007
 */
public class BtServerView extends QueryView
{
    private ViewContext _context;

    public BtServerView(ViewContext context, UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
        _context = context;
    }

    protected DataView createDataView()
    {
        DataView view = super.createDataView();

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            ButtonBar bar = view.getDataRegion().getButtonBar(DataRegion.MODE_GRID);

            ActionButton adminButton = new ActionButton(_context.cloneActionURL().setAction("admin.view").getEncodedLocalURIString(), "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            adminButton.setDisplayPermission(ACL.PERM_ADMIN);
            bar.add(adminButton);
            view.getDataRegion().setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        }
        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowColumnSeparators(true);

        return view;
    }
}
