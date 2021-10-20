/*
 * Copyright (c) 2010-2018 LabKey Corporation
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
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
    Map<String, Path> _files;  // Full list of files, if this is a directory.
    List<PipelineAction> _actions;

    public PipelineDirectoryImpl(PipeRootImpl root, String relativePath, ActionURL href)
    {
        _root = root;
        _relativePath = relativePath;
        _href = href;
        _actions = new ArrayList<>();
    }

    @Override
    public ActionURL cloneHref()
    {
        return _href.clone();
    }

    @Override
    public List<PipelineAction> getActions()
    {
        return _actions;
    }

    @Override
    public void addAction(PipelineAction action)
    {
        _actions.add(action);
    }

    protected void ensureFiles() throws IOException
    {
        // See if file list needs initialization.
        if (_files == null)
        {
            _files = new LinkedHashMap<>();

            for (Path rootPath : _root.getRootNioPaths())
            {
                assert Files.isDirectory(rootPath) : "Expected to be called with a directory";

                // Get the full set of files in the directory.
                // Use a file object that caches its directory state.
                Path dir = rootPath.resolve(_relativePath);
                Files.walkFileTree(dir, Collections.emptySet(), 1, new SimpleFileVisitor<>()
                {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    {
                        _files.put(file.getFileName().toString(), file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    @Override
    @Deprecated //Prefer Path version
    public boolean fileExists(File f)
    {
        return fileExists(f.toPath());
    }

    @Override
    public boolean fileExists(Path f)
    {
        try
        {
            ensureFiles();

            Path fileInEntry = _files.get(f.getFileName().toString());
                return (fileInEntry != null && Files.isSameFile(fileInEntry, f));
            }
        catch (IOException e)
        {
            //TODO log error
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Path> listPaths(DirectoryStream.Filter<Path> filter)
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
                for (Path path : _files.values())
                {
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

    @Override
    @Deprecated //Prefer the List<Path> version
    public File[] listFiles(FileFilter filter)
    {
        try
        {
            ensureFiles();
        }
        catch (IOException e)
        {
            // TODO: log error, but otherwise ignore
            String foo = e.getMessage();
        }

        // Actually do the filtering.
        ArrayList<File> listFiles = new ArrayList<>();
        if (filter instanceof PipelineProvider.FileEntryFilter)
            ((PipelineProvider.FileEntryFilter)filter).setFileEntry(this);
        for (Path path : _files.values())
        {
            File f = path.toFile();
            if (filter.accept(f))
                listFiles.add(f);
        }
        return listFiles.toArray(new File[listFiles.size()]);
    }

    public void orderActions()
    {
        // Sort the list of actions by size, label then first File
        _actions.sort((action1, action2) ->
        {
            int rc;
            if (action1.equals(action2))
                return 0;
            List<Path> files1 = action1.getFiles();
            List<Path> files2 = action2.getFiles();
            rc = files2.size() - files1.size();
            if (rc != 0)
                return rc;
            if (!files1.isEmpty())
            {
                rc = FileUtil.getFileName(files1.get(0)).compareToIgnoreCase(FileUtil.getFileName(files2.get(0)));
                if (rc != 0)
                    return rc;
            }
            rc = action1.getLabel().compareToIgnoreCase(action2.getLabel());
            return rc;
        });
    }

    @Override
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
        private static final FileCached NULL = new FileCached(new File("~~~null~~~"));

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

        @Override
        public boolean isDirectory()
        {
            if (_dir == null)
            {
                _dir = super.isDirectory();
            }
            return _dir.booleanValue();
        }

        @Override
        public FileCached getParentFile()
        {
            if (_parent == null)
            {
                File parentFile = super.getParentFile();
                _parent = parentFile == null ? NULL : new FileCached(parentFile);
            }
            return _parent == NULL ? null : _parent;
        }

        @Override
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

