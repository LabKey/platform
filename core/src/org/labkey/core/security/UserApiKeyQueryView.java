package org.labkey.core.security;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.MenuButton;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.core.query.CoreQuerySchema;

public class UserApiKeyQueryView extends QueryView
{
    public UserApiKeyQueryView(ViewContext ctx, UserSchema schema)
    {
        super(schema);
        QuerySettings settings =schema.getSettings(ctx, QueryView.DATAREGIONNAME_DEFAULT, CoreQuerySchema.USER_API_KEYS_TABLE_NAME);
        settings.setAllowChooseQuery(true);
        setSettings(settings);
        setShowDeleteButton(true);
        setShowExportButtons(false);

    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        bar.add(createViewButton(null));
        ActionButton deleteBtn = createDeleteButton();
        if (deleteBtn != null)
            deleteBtn.setDisplayPermission(null); // we allow deletion from any container
        bar.add(deleteBtn);
    }
}
