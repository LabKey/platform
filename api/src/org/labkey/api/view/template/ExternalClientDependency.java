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

import org.apache.log4j.Logger;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

/**
 * Handles references to resources that reside on a third-party server, such as a CDN
 */
public class ExternalClientDependency extends ClientDependency
{
    private static final Logger _log = Logger.getLogger(ExternalClientDependency.class);

    private final String _uri;

    protected ExternalClientDependency(String uri, ModeTypeEnum.Enum mode)
    {
        super(getType(uri), mode);
        _devModePath = _prodModePath = _uri = uri;
    }

    private static TYPE getType(String uri)
    {
        TYPE type = TYPE.fromString(uri);

        if (type == null)
        {
            _log.warn("External client dependency type not recognized: " + uri);
        }

        return type;
    }

    @Override
    protected void init()
    {
    }

    @Override
    protected String getUniqueKey()
    {
        return getUniqueKey(_uri, _mode);
    }

    @Override
    public String getScriptString()
    {
        return _uri;
    }
}
