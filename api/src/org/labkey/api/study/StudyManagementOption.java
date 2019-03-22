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
package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;

public class StudyManagementOption
{
    private String _title;
    private String _linkText;
    private ActionURL _linkUrl;
    private Container _container;
    private Class<? extends Permission> _permission;

    public StudyManagementOption(String title, String linkText, ActionURL linkUrl)
    {
        _title = title;
        _linkText = linkText;
        _linkUrl = linkUrl;
        _permission = AdminPermission.class; //default to Admin if not specified
    }

    public StudyManagementOption(String title, String linkText, ActionURL linkUrl, Class<? extends Permission> permission)
    {
        _title = title;
        _linkText = linkText;
        _linkUrl = linkUrl;
        _permission = permission;
    }

    public String getDescription()
    {
        return "Manage " + getTitle() + " for this study.";
    }

    public String getTitle()
    {
        return _title;
    }

    public String getLinkText()
    {
        return _linkText;
    }

    public ActionURL getLinkUrl()
    {
        return _linkUrl;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public Class<? extends Permission> getPermission()
    {
        return _permission;
    }
}
