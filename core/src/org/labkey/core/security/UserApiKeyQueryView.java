package org.labkey.core.security;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.core.query.CoreQuerySchema;

public class UserApiKeyQueryView extends QueryView
{
    public UserApiKeyQueryView(ViewContext ctx, UserSchema schema)
    {
        super(schema);
        QuerySettings settings = schema.getSettings(ctx, QueryView.DATAREGIONNAME_DEFAULT, CoreQuerySchema.USER_API_KEYS_TABLE_NAME);
        setSettings(settings);
        setShowImportDataButton(false);
        setShowExportButtons(false);
        setShowDeleteButton(false);
        setShowReports(false);
        setShowInsertNewButton(false);
    }
}
