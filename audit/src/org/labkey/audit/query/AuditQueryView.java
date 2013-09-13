/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
