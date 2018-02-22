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
package org.labkey.pipeline;

import org.labkey.api.pipeline.PipelineAction;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.pipeline.api.PipeRootImpl;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    @Override
    public List<Path> listFiles(DirectoryStream.Filter<Path> filter)
    {
        ArrayList<Path> listFiles = new ArrayList<>();
        try
        {
            if (_root.isCloudRoot())
            {
                if (_root.isValid())
                {
                    Path dirPath = _root.resolveToNioPath(_relativePath);
                    if (null != dirPath)
                    {
                        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dirPath))
                        {
                            paths.forEach(path -> {
                                try
                                {
                                    if (filter.accept(path))
                                        listFiles.add(path);
                                }
                                catch (IOException e)
                                {
                                    // Ignore
                                }
                            });
                        }
                    }
                }
            }
            else
            {
                ensureFiles();

                // Actually do the filtering.
                for (File f : _files.values())
                {
                    Path path = f.toPath();
                    if (filter.accept(path))
                        listFiles.add(path);
                }
            }
        }
        catch (IOException e)
        {
            // TODO: log error, but otherwise ignore
            String foo = e.getMessage();
        }
        return listFiles;
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
            List<Path> f = a.getFiles();
            if (null != f && f.size() > 1)
            {
                f.sort((p1, p2) -> FileUtil.getFileName(p1).compareToIgnoreCase(FileUtil.getFileName(p2)));
            }
        }

        // Sort the list of actions by size, label then first File
        _actions.sort((action1, action2) ->
        {
            int rc;
            if (action1.equals(action2))
                return 0;
            List<Path> files1 = action1.getFiles();
            List<Path> files2 = action2.getFiles();
            if (files1 == files2)
                return 0;
            if (files1 == null || files1.size() == 0)
                return -1;
            if (files2 == null || files2.size() == 0)
                return 1;
            rc = files2.size() - files1.size();
            if (rc != 0)
                return rc;
            rc = FileUtil.getFileName(files1.get(0)).compareToIgnoreCase(FileUtil.getFileName(files2.get(0)));
            if (rc != 0)
                return rc;
            rc = action1.getLabel().compareToIgnoreCase(action2.getLabel());
            return rc;
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

