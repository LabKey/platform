package org.labkey.core.security;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.core.query.CoreQuerySchema;

public class UserApiKeyQueryView extends QueryView
{
    public UserApiKeyQueryView(ViewContext ctx, UserSchema schema)
    {
        super(schema);
        QuerySettings settings = schema.getSettings(ctx, QueryView.DATAREGIONNAME_DEFAULT, CoreQuerySchema.USER_API_KEYS_TABLE_NAME);
        setSettings(settings);
        // This is needed so the print button doesn't show up.
        setShowExportButtons(false);
    }


    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        bar.add(createViewButton(null));

        ActionButton deleteBtn = new ActionButton(new ActionURL(SecurityController.DeleteApiKeysAction.class, getContainer()).addReturnURL(getReturnURL()), "Delete");
        deleteBtn.setIconCls("trash");
        deleteBtn.setActionType(ActionButton.Action.POST);
        deleteBtn.setDisplayPermission(null); // we allow deletion from any container
        deleteBtn.setRequiresSelection(true, "Are you sure you want to delete the selected API key?", "Are you sure you want to delete the selected API keys?");
        bar.add(deleteBtn);

        setShowReports(false);
        setShowInsertNewButton(false);
        setShowImportDataButton(false);
    }
}
