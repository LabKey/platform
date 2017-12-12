/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.labkey.api.view.ActionURL;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction layer over a directory on the file system. Implementations may
 * perform caching to avoid many round-trips to the file system to get metadata,
 * such as the list of children and their types.
 *
 * This is important because some network file shares can have very high latency for metadata operations,
 * so we want to avoid asking for the same attributes multiple times.
 *
 * User: jeckels
 * Date: Aug 17, 2010
 */
public interface PipelineDirectory
{
    ActionURL cloneHref();
    List<PipelineAction> getActions();
    void addAction(PipelineAction action);
    boolean fileExists(File f);

    /**
     * Returns a filtered set of files with cached directory/file status.
     *
     * @param filter The filter to use on the listed files.
     * @return List of filtered files.
     */
    File[] listFiles(FileFilter filter);

    List<Path> listFiles(DirectoryStream.Filter<Path> filter);

    String getPathParameter();

    /** @return the path relative to the root */
    String getRelativePath();
}
