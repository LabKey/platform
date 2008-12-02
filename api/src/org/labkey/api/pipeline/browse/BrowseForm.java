/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.pipeline.browse;

import org.labkey.api.view.ViewFormData;
import org.labkey.api.view.ActionURL;

import java.util.*;

abstract public class BrowseForm extends ViewFormData
{
    public enum Param
    {
        path,
        fileFilter,
        file,
    }


    private String path;
    private String fileFilter;
    private String[] file = new String[0];

    public String[] getFile()
    {
        return file;
    }

    public void setFile(String[] file)
    {
        this.file = file;
    }

    public String getFileFilter()
    {
        return fileFilter;
    }

    public void setFileFilter(String fileFilter)
    {
        this.fileFilter = fileFilter;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        if (path == null)
        {
            // Set path to empty string to distinguish from "path" not being specified in the URL (in which case
            // use PipeRoot.getStartingPath.
            path = "";
        }
        this.path = path;
    }

    abstract public ActionURL getActionURL();
    abstract public String getActionText();

    public boolean isMultiSelect()
    {
        return false;
    }
    public boolean isDirectoriesSelectable()
    {
        return false;
    }
    public Map<String, ? extends FileFilter> getFileFilterOptions()
    {
        return Collections.singletonMap("all", FileFilter.allFiles);
    }
    public FileFilter getFileFilterObject()
    {
        Map<String, ? extends FileFilter> options = getFileFilterOptions();
        FileFilter ret = options.get(fileFilter);
        if (ret != null)
            return ret;
        return options.values().iterator().next();
    }
    public Comparator<? super BrowseFile> getBrowseFileComparator()
    {
        return new Comparator<BrowseFile>() {

            public int compare(BrowseFile o1, BrowseFile o2)
            {
                if (o1.isDirectory())
                {
                    if (!o2.isDirectory())
                    {
                        return -1;
                    }
                }
                else
                {
                    if (!o1.isDirectory())
                    {
                        return 1;
                    }
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        };
    }

    final public String paramName(Param param)
    {
        return param.toString();
    }
}
