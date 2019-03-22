/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.api.pipeline.cmd;

import java.util.ArrayList;
import java.util.List;

/**
 * <code>PathWithSwitch</code>
*/
public class PathWithSwitch extends PathToCommandArgs
{
    private String _switchName;

    public String getSwitchName()
    {
        return _switchName;
    }

    public void setSwitchName(String switchName)
    {
        _switchName = switchName;
    }

    public List<String> toArgs(String[] paths)
    {
        ArrayList<String> args = new ArrayList<>();
        for (String path : paths)
        {
            if (path != null && path.length() > 0)
                args.addAll(getSwitchFormat().format(getSwitchName(), path));
        }
        return args;
    }
}
