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
package org.labkey.api.files;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.io.File;

/**
 * Listener that gets notified when the server moves files or directories on the file system. Method is invoked
 * once at the root level of the move, not recursively for every child file.
 * User: jeckels
 * Date: 11/7/12
 */
public interface FileListener
{
    /**
     * Called AFTER the file has already been created on disk
     * @param created newly created resource
     * @param user if available, the user who initiated the create
     * @param container if available, the container in which the create was initiated
     */
    public void fileCreated(@NotNull File created, @Nullable User user, @Nullable Container container);

    /**
     * Called AFTER the file has already been moved on disk
     * @param src
     * @param dest
     * @param user if available, the user who initiated the move
     * @param container if available, the container in which the move was initiated
     */
    public void fileMoved(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container);
}
