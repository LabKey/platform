/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.view.template;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.Path;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

/**
 * Handle a reference to a resource that resides in the file system, such as a JS or CSS file
 */
public class FilePathClientDependency extends ClientDependency
{
    private static final Logger _log = LogManager.getLogger(FilePathClientDependency.class);

    protected final Path _filePath;

    protected FilePathClientDependency(Path filePath, ModeTypeEnum.@NotNull Enum mode, TYPE primaryType)
    {
        super(primaryType, mode);
        _filePath = filePath;
    }

    @Override
    protected void init()
    {
        processScript(_filePath);
    }

    @Override
    protected String getUniqueKey()
    {
        assert _filePath != null;
        return getUniqueKey(_filePath.toString(), _mode);
    }

    @Override
    public String getScriptString()
    {
        return _filePath.toString();
    }

    private void processScript(Path filePath)
    {
        TYPE type = TYPE.fromPath(filePath);

        if (type == null)
        {
            _log.warn("Invalid file type for resource: " + filePath);
            return;
        }

        handleScript(filePath);
    }

    protected void handleScript(Path filePath)
    {
        if (!_mode.equals(ModeTypeEnum.PRODUCTION))
            _devModePath = filePath.toString();

        if (!_mode.equals(ModeTypeEnum.DEV))
            _prodModePath = filePath.toString();
    }
}
