/*
 * Copyright (c) 2010 LabKey Corporation
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
import java.util.List;

/**
 * User: jeckels
 * Date: Aug 17, 2010
 */
public interface PipelineDirectory
{
    public ActionURL cloneHref();
    public List<PipelineAction> getActions();
    public void addAction(PipelineAction action);
    public boolean fileExists(File f);

    /**
     * Returns a filtered set of files with cached directory/file status.
     * The function also uses a map to avoid looking for the same fileset
     * multiple times.
     *
     * @param filter The filter to use on the listed files.
     * @return List of filtered files.
     */
    public File[] listFiles(FileFilter filter);

    public String getPathParameter();

    /** @return the path relative to the root */
    public String getRelativePath();
}
