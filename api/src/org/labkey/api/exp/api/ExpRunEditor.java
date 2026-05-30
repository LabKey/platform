/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * Identifies a run editor entry point like the Sample Derivation run editor in the Provenance module.  An ExpRunEditor
 * class instance can be registered in the ExperimentService and create run links will be added on the Runs grid,
 * samples content grid and file browser webpart.  Right now this only supports a single run editor.
 */
public class ExpRunEditor
{
    private final ActionURL _editUrl;
    private final String _displayName;
    private final String _protocolName;

    public ExpRunEditor(String displayName, String protocolName, ActionURL editUrl)
    {
        _displayName = displayName;
        _protocolName = protocolName;
        _editUrl = editUrl;
    }

    public ActionURL getEditUrl(Container c)
    {
        // new action url so parameters don't get added repeatedly to _editUrl
        ActionURL editUrl = _editUrl.clone();
        return editUrl.setContainer(c);
    }

    public String getDisplayName()
    {
        return _displayName;
    }

    public String getProtocolName()
    {
        return _protocolName;
    }

    public boolean isProtocolEditor(ExpProtocol protocol)
    {
        return protocol.getName().equals(_protocolName);
    }
}
