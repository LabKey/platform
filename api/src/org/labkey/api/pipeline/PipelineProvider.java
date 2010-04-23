/*
 * Copyright (c) 2005-2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.module.Module;
import org.springframework.web.servlet.mvc.Controller;

import java.io.*;
import java.net.URI;
import java.util.*;

/**
 */
abstract public class PipelineProvider
{
    public static final String CAPTION_RETRY_BUTTON = "Retry";

    public enum Params { path }

    private boolean _showActionsIfModuleInactive;

    // UNDONE: should probably extend NavTree
    public static class PipelineDirectory
    {
        URI _uri;
        ActionURL _href;
        Map<String, File> _files;  // Full list of files, if this is a directory.
        List<PipelineAction> _actions;

        public PipelineDirectory(URI uri, ActionURL href)
        {
            _uri = uri;
            _href = href;
            _actions = new ArrayList<PipelineAction>();
        }

        public URI getURI()
        {
            return _uri;
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
                _files = new LinkedHashMap<String, File>();

                assert URIUtil.isDirectory(_uri) : "Expected to be called with a directory";

                // Get the full set of files in the directory.
                // Use a file object that caches its directory state.
                File dir = new FileCached(new File(_uri));
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

        public boolean fileExists(File f)
        {
            ensureFiles();

            File fileInEntry = _files.get(f.getName());
            return (fileInEntry != null && fileInEntry.equals(f));
        }

        /**
         * Returns a filtered set of files with cached directory/file status.
         * The function also uses a map to avoid looking for the same fileset
         * multiple times.
         *
         * @param filter The filter to usae on the listed files.
         * @return List of filtered files.
         */
        public File[] listFiles(FileFilter filter)
        {
            ensureFiles();

            // Actually do the filtering.
            ArrayList<File> listFiles = new ArrayList<File>();
            if (filter instanceof FileEntryFilter)
                ((FileEntryFilter)filter).setFileEntry(this);
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

    }

    public static abstract class FileEntryFilter implements FileFilter
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
    }

    public static class FileTypesEntryFilter extends FileEntryFilter
    {
        private FileType[] _initialFileTypes;

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

        public boolean accept(File f)
        {
            return accept(f, true);
        }

        public boolean accept(File f, boolean checkSiblings)
        {
            if (_initialFileTypes != null)
            {
                for (int i = 0; i < _initialFileTypes.length; i++)
                {
                    FileType ft = _initialFileTypes[i];
                    if (ft.isType(f))
                    {
                        File dir = f.getParentFile();
                        String basename = ft.getBaseName(f);

                        // If any of the preceding types exist, then don't include this one.
                        while (--i >= 0)
                        {
                            if (fileExists(_initialFileTypes[i].newFile(dir, basename)))
                                return false;
                        }

                        int indexMatch = ft.getIndexMatch(f);
                        if (checkSiblings && indexMatch > 0)
                        {
                            File[] siblings = dir.listFiles();
                            if (siblings != null)
                            {
                                for (File sibling : siblings)
                                {
                                    if (!sibling.equals(f) &&
                                        ft.getBaseName(sibling).equals(basename) &&
                                        ft.isType(sibling.getName()) &&
                                        ft.getIndexMatch(sibling) < indexMatch)
                                    {
                                        return false;
                                    }
                                }
                            }
                        }

                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static class StatusAction
    {
        String _label;

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
                _children = new FileCached[files.length];
                for (int i = 0; i < files.length; i++)
                {
                    _children[i] = new FileCached(files[i]);
                    _children[i]._parent = this;
                }
            }
            return _children;
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
    public void initSystemDirectory(File rootDir, File systemDir)
    {        
    }

    /**
     * Return true, if the file name should present a link for viewing its
     * contents on the details page for status associated with this provider.
     *
     * @param container the <code>Container</code> for the status entery
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
     * @param sf the entry to delete
     */
    public void preDeleteStatusFile(PipelineStatusFile sf)
    {
    }

    public Module getOwningModule()
    {
        return _owningModule;
    }

    /**
     * @return Web part shown on the setup page.
     */
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
     * Allows the provider to add action buttons to the details page of one
     * of its status entries.
     *
     * @return List of actions to add to the details page
     */
    public List<StatusAction> addStatusActions()
    {
        List<PipelineProvider.StatusAction> actions = new ArrayList<PipelineProvider.StatusAction>();
        actions.add(new PipelineProvider.StatusAction(CAPTION_RETRY_BUTTON)
        {
            @Override
            public boolean isVisible(PipelineStatusFile statusFile)
            {
                return PipelineJob.ERROR_STATUS.equals(statusFile.getStatus()) && statusFile.getJobStore() != null;
            }
        });
        return actions;
    }

    /**
     * Allows the provider to handle the user clicking on one of the action
     * buttons provided through getStatusActions.
     *
     * @param name The name of the action clicked
     * @param sf   The StatusFile object on which the action is to be performed
     */
    public ActionURL handleStatusAction(ViewContext ctx, String name, PipelineStatusFile sf)
            throws HandlerException
    {
        if (!PipelineProvider.CAPTION_RETRY_BUTTON.equals(name) ||
                !PipelineJob.ERROR_STATUS.equals(sf.getStatus()))
            return null;

        try
        {
            PipelineJobService.get().getJobStore().retry(sf);
        }
        // CONSIDER: Narrow this net further? 
        catch (Exception e)
        {
            throw new HandlerException(e);
        }

        return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(ctx.getContainer(), sf.getRowId());
    }

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
    }

    protected String createActionId(Class action, String description)
    {
        if (description != null)
            return action.getName() + ':' + description;
        else
            return action.getName();
    }

    protected void addAction(String actionId, URLHelper actionURL, String description, PipelineDirectory entry, File[] files,
                             boolean allowMultiSelect, boolean allowEmptySelect, boolean includeAll)
    {
        if (!includeAll && (files == null || files.length == 0))
            return;

        entry.addAction(new PipelineAction(actionId, description, actionURL, files, allowMultiSelect, allowEmptySelect));
    }

    protected void addAction(String actionId, Class<? extends Controller> action, String description, PipelineDirectory directory, File[] files,
                             boolean allowMultiSelect, boolean allowEmptySelect, boolean includeAll)
    {
        if (!includeAll && (files == null || files.length == 0))
            return;
        ActionURL actionURL = directory.cloneHref();
        actionURL.setAction(action);
        directory.addAction(new PipelineAction(actionId, description, actionURL, files, allowMultiSelect, allowEmptySelect));
      }

    /**
     * Returns true if a provider wants to show file actions even if the provider module is not active
     * in a container.
     * @return
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

    public List<PipelineActionConfig> getDefaultActionConfig()
    {
        return Collections.emptyList();
    }
}
