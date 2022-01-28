/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.labkey.api.security.permissions.CanUseSendMessageApiPermission;

public class CanUseSendMessageApi extends AbstractRootContainerRole
{
    public CanUseSendMessageApi()
    {
        super("Use SendMessage API", "Allows users to invoke the send message API. This API can be called by code that sends emails to users and potentially non-users of the system.",
                true,
                CanUseSendMessageApiPermission.class);
    }
}
