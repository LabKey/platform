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
package org.labkey.api.data;

import org.labkey.api.util.GUID;
import org.labkey.api.util.Path;

public class ContainerServiceImpl implements ContainerService
{
    @Override
    public Container getForId(GUID id)
    {
        return ContainerManager.getForId(id);
    }

    @Override
    public Container getForId(String id)
    {
        return ContainerManager.getForId(id);
    }

    @Override
    public Container getForPath(Path path)
    {
        return ContainerManager.getForPath(path);
    }

    @Override
    public Container getForPath(String path)
    {
        return ContainerManager.getForPath(path);
    }
}
