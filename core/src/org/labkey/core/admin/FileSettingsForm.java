/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.core.admin;

/**
* User: jeckels
* Date: Sep 26, 2011
*/
public class FileSettingsForm
{
    private String _rootPath;
    private boolean _upgrade;

    public String getRootPath()
    {
        return _rootPath;
    }

    public void setRootPath(String rootPath)
    {
        _rootPath = rootPath;
    }

    public boolean isUpgrade()
    {
        return _upgrade;
    }

    public void setUpgrade(boolean upgrade)
    {
        _upgrade = upgrade;
    }
}
