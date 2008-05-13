/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.attachments;

import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.springframework.web.servlet.mvc.Controller;


/**
 * User: mbellew
 * Date: May 6, 2004
 * Time: 11:36:07 AM
 */
public class DownloadURL extends ActionURL
{
    public DownloadURL(Class<? extends Controller> actionClass, Container c, String entityId, String filename)
    {
        super(actionClass, c);
        init(c, entityId, filename);
    }

    @Deprecated
    public DownloadURL(String pageFlow, String containerPath, String entityId, String filename)
    {
        super(pageFlow, "download.view", containerPath);
        init(containerPath, entityId, filename);
    }

    private void init(String containerPath, String entityId, String filename)
    {
        if (null == containerPath)
            throw new IllegalArgumentException();

        init(entityId, filename);
    }

    private void init(Container c, String entityId, String filename)
    {
        if (null == c)
            throw new IllegalArgumentException();

        init(entityId, filename);
    }

    private void init(String entityId, String filename)
    {
        if (null == entityId)
            throw new IllegalArgumentException();

        addParameter("entityId", entityId);
        setFileName(filename);
    }

    public void setFileName(String filename)
    {
        if (null == filename)
        {
            this.deleteParameter("name");
            return;
        }
        if (-1 != filename.indexOf("/"))
            throw new IllegalArgumentException();
        this.replaceParameter("name", filename);
    }
}
