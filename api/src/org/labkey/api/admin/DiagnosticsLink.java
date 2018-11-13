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
package org.labkey.api.admin;

import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;

public class DiagnosticsLink
{
    private ActionURL _linkUrl;
    private Class<? extends Permission> _permission;

    public DiagnosticsLink(ActionURL linkUrl)
    {
        this(linkUrl, AdminPermission.class); //default to Admin permission if not specified
    }

    public DiagnosticsLink(ActionURL linkUrl, Class<? extends Permission> permission)
    {
        _linkUrl = linkUrl;
        _permission = permission;
    }

    public ActionURL getLinkUrl()
    {
        return _linkUrl;
    }

    public Class<? extends Permission> getPermission()
    {
        return _permission;
    }
}
