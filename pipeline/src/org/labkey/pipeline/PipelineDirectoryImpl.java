/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.pipeline;

import org.labkey.api.pipeline.PipelineAction;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.pipeline.api.PipeRootImpl;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Aug 17, 2010
 */
public class PipelineDirectoryImpl implements PipelineDirectory
{
    PipeRootImpl _root;
    String _relativePath;
    ActionURL _href;
    Map<String, File> _files;  // Full list of files, if this is a directory.
    List<PipelineAction> _actions;

    public PipelineDirectoryImpl(PipeRootImpl root, String relativePath, ActionURL href)
    {
        _root = root;
        _relativePath = relativePath;
        _href = href;
        _actions = new ArrayList<>();
    }

    public ActionURL cloneHref()
    {
        return _href.clone();
    }

    public List<PipelineAction> getActions()
    {
        return _actions;
    }

    public void addAction(PipelineAction action)
    {
        _actions.add(action);
    }

    protected void ensureFiles()
    {
        // See if file list needs initialization.
        if (_files == null)
        {
            _files = new LinkedHashMap<>();

            for (File rootPath : _root.getRootPaths())
            {
                assert !rootPath.isFile() : "Expected to be called with a directory";

                // Get the full set of files in the directory.
                // Use a file object that caches its directory state.
                File dir = new FileCached(new File(rootPath, _relativePath));
                File[] files = dir.listFiles();

                if (files != null)
                {
                    for (File file : files)
                    {
                        _files.put(file.getName(), file);
                    }
                }
            }
        }
    }

    public boolean fileExists(File f)
    {
        ensureFiles();

        File fileInEntry = _files.get(f.getName());
        return (fileInEntry != null && fileInEntry.equals(f));
    }

    public File[] listFiles(FileFilter filter)
    {
        ensureFiles();

        // Actually do the filtering.
        ArrayList<File> listFiles = new ArrayList<>();
        if (filter instanceof PipelineProvider.FileEntryFilter)
            ((PipelineProvider.FileEntryFilter)filter).setFileEntry(this);
        for (File f : _files.values())
        {
            if (filter.accept(f))
                listFiles.add(f);
        }
        return listFiles.toArray(new File[listFiles.size()]);
    }

    public void orderActions()
    {
        // Sort each action's array of Files
        for (PipelineAction a : _actions)
        {
            File[] f = a.getFiles();
            if (null != f && f.length > 1)
            {
                Arrays.sort(f, new Comparator<File>()
                {
                    public int compare(File f1, File f2)
                    {
                        return f1.getPath().compareToIgnoreCase(f2.getPath());
                    }
                });
            }
        }

        // Sort the list of actions by size, label then first File
        Collections.sort(_actions, new Comparator<PipelineAction>()
        {
            public int compare(PipelineAction action1, PipelineAction action2)
            {
                int rc;
                if (action1.equals(action2))
                    return 0;
                File[] files1 = action1.getFiles();
                File[] files2 = action2.getFiles();
                if (files1 == files2)
                    return 0;
                if (files1 == null || files1.length == 0)
                    return -1;
                if (files2 == null || files2.length == 0)
                    return 1;
                rc = files2.length - files1.length;
                if (rc != 0)
                    return rc;
                rc = files1[0].getPath().compareToIgnoreCase(files2[0].getPath());
                if (rc != 0)
                    return rc;
                rc = action1.getLabel().compareToIgnoreCase(action2.getLabel());
                return rc;
            }
        });
    }

    public String getPathParameter()
    {
        return _href.getParameter("path");
    }

    @Override
    public String getRelativePath()
    {
        return _relativePath;
    }

    public static class FileCached extends File
    {
        private Boolean _dir;
        private FileCached _parent;
        private FileCached[] _children;

        public FileCached(File f)
        {
            super(f.getPath());
        }

        public boolean isFile()
        {
            return !isDirectory();
        }

        public boolean isDirectory()
        {
            if (_dir == null)
            {
                _dir = super.isDirectory();
            }
            return _dir.booleanValue();
        }

        public FileCached getParentFile()
        {
            if (_parent == null)
            {
                _parent = new FileCached(super.getParentFile());
            }
            return _parent;
        }

        public File[] listFiles()
        {
            if (_children == null)
            {
                File[] files = super.listFiles();
                if (files == null)
                {
                    _children = new FileCached[0];
                }
                else
                {
                    _children = new FileCached[files.length];
                    for (int i = 0; i < files.length; i++)
                    {
                        _children[i] = new FileCached(files[i]);
                        _children[i]._parent = this;
                    }
                }
            }
            return _children;
        }
    }

}

