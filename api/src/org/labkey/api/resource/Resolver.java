/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
package org.labkey.api.resource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Path;

/**
 * User: kevink
 * Date: Mar 13, 2010 9:30:21 AM
 */
public interface Resolver
{
    Path getRootPath();

    /**
     * Locate a Resource at the given path.
     * @param path
     * @return
     */
    @Nullable Resource lookup(Path path);


    default void addLink(@NotNull Path from, @NotNull Path target, String indexPage)
    {
        throw new UnsupportedOperationException();
    }


    default void removeLink(Path from)
    {
        throw new UnsupportedOperationException();
    }
}
