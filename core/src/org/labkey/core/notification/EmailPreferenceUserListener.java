/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.core.notification;

import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

public class EmailPreferenceUserListener implements UserManager.UserListener
{
    @Override
    public void userDeletedFromSite(User user)
    {
        //when user is deleted from site, remove any corresponding record from EmailPrefs table.
        Table.delete(CoreSchema.getInstance().getTableInfoEmailPrefs(), new SimpleFilter(FieldKey.fromParts("UserId"), user.getUserId()));
    }
}
