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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <code>PathToCommandArgs</code>
*/
public abstract class PathToCommandArgs extends TaskPathToCommandArgs
{
    @Override
    public List<String> toArgsInner(CommandTask task, Map<String, String> params, Set<TaskToCommandArgs> visited) throws IOException
    {
        return toArgs(getPaths(task));
    }

    abstract public List<String> toArgs(String[] paths);
}
