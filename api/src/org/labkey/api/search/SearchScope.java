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
package org.labkey.api.search;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

/**
 * Options for how widely or narrowly to search on the server, based on the number of containers to include.
 * User: adam
 * Date: 2/18/12
 */
public enum SearchScope
{
    All() {
        @Override
        public Container getRoot(Container c)
        {
            return ContainerManager.getRoot();
        }
    },
    Project() {
        @Override
        public Container getRoot(Container c)
        {
            return c.getProject();
        }
    },
    FolderAndSubfolders() {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }
    },
    Folder() {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }

        @Override
        public boolean isRecursive()
        {
            return false;
        }
    };

    public abstract Container getRoot(Container c);

    public boolean isRecursive()
    {
        return true;
    }
}
