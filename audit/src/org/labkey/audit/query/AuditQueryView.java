package org.labkey.audit.query;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.springframework.validation.Errors;

/**
 * User: kevink
 * Date: 8/29/13
 *
 * Most audit tables don't allow inserting, but the ClientApiAuditProvider's table allows
 * inserts from the client api.  This query view disables the insert buttons in the html UI
 * while still allowing the LABKEY.Query.insertRows() api to still work.
 *
 * @see org.labkey.api.audit.ClientApiAuditProvider#createTableInfo(org.labkey.api.query.UserSchema)
 */
public class AuditQueryView extends QueryView
{
    public AuditQueryView(AuditQuerySchema schema, QuerySettings settings, Errors errors)
    {
        super(schema, settings, errors);

    }

    @Override
    public boolean showInsertNewButton()
    {
        return false;
    }

    @Override
    public boolean showImportDataButton()
    {
        return false;
    }

}
