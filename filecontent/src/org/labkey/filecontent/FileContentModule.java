/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.filecontent;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.webdav.WebdavService;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;


public class FileContentModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(FileContentModule.class);
    public static final String NAME = "FileContent";

    public FileContentModule()
    {
        super(NAME, 8.20, null, false, new FilesWebPart.Factory(HttpView.BODY), new FilesWebPart.Factory("right"));
    }

    protected void init()
    {
        addController("filecontent", FileContentController.class);
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        WebdavService.addProvider(new FileWebdavProvider());
    }

    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set();
    }
}