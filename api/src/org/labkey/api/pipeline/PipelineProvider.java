/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A source of things that can be done to files in the pipeline directory. Standard use cases include doing analysis
 * of data files and importing results into the database. Implementations should be registered via a call to
 * {@link PipelineService}'s registerPipelineProvider method during module startup.
 */
abstract public class PipelineProvider
{
    private static final Logger _log = LogHelper.getLogger(PipelineProvider.class, "PipelineProvider and subclasses' execution");

    public enum Params { path }

    private boolean _showActionsIfModuleInactive;

    public static abstract class FileEntryFilter implements FileAnalysisTaskPipeline.FilePathFilter
    {
        private PipelineDirectory _entry;

        public void setFileEntry(PipelineDirectory entry)
        {
            _entry = entry;
        }

        public boolean fileExists(File f)
        {
            if (_entry == null)
                return f.exists();
            return _entry.fileExists(f);
        }

        public boolean fileExists(Path f)
        {
            if (_entry == null)
                return Files.exists(f);
            return _entry.fileExists(f);
        }

        @Override
        public boolean accept(Path file)
        {
            return accept(file.toFile());
        }
    }

    public static class FileTypesEntryFilter extends FileEntryFilter
    {
        private final FileType[] _initialFileTypes;

        public FileTypesEntryFilter(List<FileType> initialFileTypes)
        {
            if (initialFileTypes == null)
            {
                _initialFileTypes = new FileType[0];
            }
            else
            {
                _initialFileTypes = initialFileTypes.toArray(new FileType[initialFileTypes.size()]);
            }
        }
        
        public FileTypesEntryFilter(FileType initialFileType, FileType... otherFileTypes)
        {
            this(appendToArray(otherFileTypes, initialFileType));
        }

        private static FileType[] appendToArray(FileType[] otherFileTypes, FileType initialFileType)
        {
            if (otherFileTypes == null)
            {
                return new FileType[] { initialFileType };
            }

            FileType[] result = new FileType[otherFileTypes.length + 1];
            result[0] = initialFileType;
            System.arraycopy(otherFileTypes, 0, result, 1, otherFileTypes.length);
            return result;
        }

        public FileTypesEntryFilter(FileType[] initialFileTypes)
        {
            _initialFileTypes = initialFileTypes;
        }

        @Override
        @Deprecated //Prefer Path version
        public boolean accept(File f)
        {
            return accept(f.toPath(), true);
        }

        @Override
        public boolean accept(Path f)
        {
            return accept(f, true);
        }

        public boolean accept(Path f, boolean checkSiblings)
        {
            if (_initialFileTypes != null)
            {
                for (int i = 0; i < _initialFileTypes.length; i++)
                {
                    FileType ft = _initialFileTypes[i];
                    if (ft.isType(f))
                    {
                        Path dir = f.getParent();
                        String basename = ft.getBaseName(f);

                        // If any of the preceding types exist, then don't include this one.
                        while (--i >= 0)
                        {
                            if (fileExists(_initialFileTypes[i].newFile(dir, basename)))
                                return false;
                        }

                        int indexMatch = ft.getIndexMatch(f);
                        if (checkSiblings && indexMatch > 0 && ft.isExtensionsMutuallyExclusive())
                        {
                            try (Stream<Path> pathStream = Files.walk(dir, 0, FileVisitOption.FOLLOW_LINKS))
                            {
                                return pathStream.noneMatch(sibling ->
                                    !sibling.equals(f) &&
                                    ft.getBaseName(sibling).equals(basename) &&
                                    ft.isType(sibling.getFileName().toString()) &&
                                    ft.getIndexMatch(sibling) < indexMatch
                                );
                            }
                            catch (IOException e)
                            {
                                _log.error("Error matching siblings with filter", e);
                                return false;
                            }
                        }

                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Operations that can be performed on a job belonging to this provider from the job's status details page.
     * Cancel and retry are examples.
     */
    public static class StatusAction
    {
        private final String _label;

        public StatusAction(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        public boolean isVisible(PipelineStatusFile statusFile)
        {
            return true;
        }
    }

    protected String _name;
    private final Module _owningModule;

    public PipelineProvider(String name, Module owningModule)
    {
        _name = name;
        _owningModule = owningModule;
    }

    /**
     * Returns a string name associated with this provider, by which it can be
     * retrieved from the <code>PipelineService</code>.
     *
     * @return the name of the provider
     */
    public String getName()
    {
        return _name;
    }

    /**
     * Override to do an work necessary immediately after a new system
     * directory is created in a pipeline root.

     * @param rootDir the pipeline root directory on disk
     * @param systemDir the system directory itself
     */
    @Deprecated
    public void initSystemDirectory(File rootDir, File systemDir)
    {
        if (null != rootDir && null != systemDir)
            initSystemDirectory(rootDir.toPath(), systemDir.toPath());
    }

    public void initSystemDirectory(Path rootDir, Path systemDir)
    {
    }

    /**
     * Return true, if the file name should present a link for viewing its
     * contents on the details page for status associated with this provider.
     *
     * @param container the <code>Container</code> for the status entry
     * @param name the file name
     * @param basename the base name associated with the status @return true if link should be displayed
     */
    public boolean isStatusViewableFile(Container container, String name, String basename)
    {
        return PipelineJob.FT_LOG.isMatch(name, basename);
    }

    /**
     * Override to do any extra work necessary before deleting a status entry.
     *
     * @param user
     * @param sf the entry to delete
     */
    public void preDeleteStatusFile(User user, PipelineStatusFile sf)
    {
    }

    public Module getOwningModule()
    {
        return _owningModule;
    }

    public boolean supportsCloud()
    {
        return false;
    }

    /**
     * @return Web part shown on the setup page.
     */
    @Nullable
    public HttpView getSetupWebPart(Container container)
    {
        // No setup.
        return null;
    }

    /**
     * Allows the provider to add actions to files in the current directory
     * during pipeline root navigation by the user.
     *
     * @param context The ViewContext for the current request
     * @param pr the <code>PipeRoot</code> object for the current context
     * @param directory directory to scan for possible actions
     * @param includeAll add all actions from this provider even if there are no files of interest in the pipeline directory
     */
    public abstract void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll);

    /**
     * Local exception type to throw from handleStatusAction.
     */
    public static class HandlerException extends Exception
    {
        public HandlerException(Throwable cause)
        {
            super(cause);
        }

        public HandlerException(String message, Throwable cause)
        {
            super(message, cause);
        }

        public HandlerException(String message)
        {
            super(message);
        }
    }

    protected String createActionId(Class action, String description)
    {
        if (description != null)
            return action.getName() + ':' + description;
        else
            return action.getName();
    }

    @Deprecated //Prefer List<Path> version
    protected void addAction(String actionId, URLHelper actionURL, String description, PipelineDirectory entry, File[] files,
                             boolean allowMultiSelect, boolean allowEmptySelect, boolean includeAll)
    {
        if (!includeAll && (files == null || files.length == 0))
            return;

        entry.addAction(new PipelineAction(actionId, description, actionURL, files, allowMultiSelect, allowEmptySelect));
    }

    @Deprecated //Prefer List<Path> version
    protected void addAction(String actionId, Class<? extends Controller> action, String description, PipelineDirectory directory, File[] files,
                             boolean allowMultiSelect, boolean allowEmptySelect, boolean includeAll)
    {
        if (!includeAll && (files == null || files.length == 0))
            return;
        ActionURL actionURL = directory.cloneHref();
        actionURL.setAction(action);
//        Uncomment to debug GWT app - can't just edit the URL and reload because it's a POST
//        actionURL.addParameter("gwt.codesvr", "127.0.0.1:9997");
        directory.addAction(new PipelineAction(actionId, description, actionURL, files, allowMultiSelect, allowEmptySelect));
      }

    protected void addAction(String actionId, URLHelper actionURL, String description, PipelineDirectory entry, List<Path> files,
                             boolean allowMultiSelect, boolean allowEmptySelect, boolean includeAll)
    {
        if (!includeAll && (files == null || files.size() == 0))
            return;

        entry.addAction(new PipelineAction(actionId, description, actionURL, files, allowMultiSelect, allowEmptySelect));
    }

    protected void addAction(String actionId, Class<? extends Controller> action, String description, PipelineDirectory directory, List<Path> files,
                             boolean allowMultiSelect, boolean allowEmptySelect, boolean includeAll)
    {
        if (!includeAll && (files == null || files.size() == 0))
            return;
        ActionURL actionURL = directory.cloneHref();
        actionURL.setAction(action);
        directory.addAction(new PipelineAction(actionId, description, actionURL, files, allowMultiSelect, allowEmptySelect));
      }

    /**
     * Returns true if a provider wants to show file actions even if the provider module is not active
     * in a container.
     */
    public boolean isShowActionsIfModuleInactive()
    {
        return _showActionsIfModuleInactive;
    }

    protected void setShowActionsIfModuleInactive(boolean showActionsIfModuleInactive)
    {
        _showActionsIfModuleInactive = showActionsIfModuleInactive;
    }

    /**
     * Return true if this provider believes that it is in use in the container, and
     * also can handle overlapping pipeline roots.
     * A pipeline provider should not return "true" without first checking that it is active (i.e. that the
     * current container's folder type is one that makes use of this pipeline provider.
     * Many modules (e.g. MS2) have trouble with overlapping pipeline roots.  It is important that in MS2 folders
     * the user be shown the warning.
     */
    public boolean suppressOverlappingRootsWarning(ViewContext context)
    {
        return false;
    }

    /** Calculate the available set of actions */
    protected List<PipelineActionConfig> getDefaultActionConfigSkipModuleEnabledCheck(Container container)
    {
        return Collections.emptyList();
    }

    /** Check if this provider should add actions based on the enabled modules, and then calculate the available set */
    public List<PipelineActionConfig> getDefaultActionConfig(Container container)
    {
        if (_showActionsIfModuleInactive || container.getActiveModules().contains(getOwningModule()))
        {
            return getDefaultActionConfigSkipModuleEnabledCheck(container);
        }
        return Collections.emptyList();
    }
}
