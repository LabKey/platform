/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.core.admin.writer;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.apache.log4j.Logger;

import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderExportContext extends AbstractFolderContext
{
    private final Set<String> _dataTypes;
    private String _format = "new";

    public FolderExportContext(User user, Container c, Set<String> dataTypes, String format, Logger logger)
    {
        super(user, c, FolderXmlWriter.getFolderDocument(), logger, null);
        _dataTypes = dataTypes;
        _format = format;
    }

    public Set<String> getDataTypes()
    {
        return _dataTypes;
    }

    public String getFormat()
    {
        return _format;
    }
}
