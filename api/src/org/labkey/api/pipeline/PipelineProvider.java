/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import java.io.File;
import java.io.FileFilter;
import java.net.URI;
import java.util.*;

/**
 */
abstract public class PipelineProvider
{
    public enum Params { path }

    // UNDONE: should probably extend NavTree
    public static class FileEntry
    {
        URI _uri;
        String _label;
        ActionURL _href;
        String _imageURL;
        boolean _isDirectory;
        Map<String, File> _files;  // Full list of files, if this is a directory.
        Map<Class, File[]> _fileSets;  // Sets of files already listed for this entry.
        List<FileAction> _actions;

        public FileEntry(URI uri, ActionURL href, boolean open)
        {
            _label = URIUtil.getFilename(uri);
            _isDirectory = URIUtil.isDirectory(uri);
            _uri = uri;
            _href = href.clone();
            _actions = new ArrayList<FileAction>();
            ActionURL imageURL = _href.clone();
            imageURL.deleteParameters();
            imageURL.setAction((String)null);
            imageURL.setExtraPath("images");
            if (_isDirectory)
            {
                if (open)
                    imageURL.setAction("folder_open.gif");
                else
                    imageURL.setAction("folder.gif");
            }
            else
            {
                imageURL.setAction("file.gif");
            }
            _imageURL = imageURL.toString();
        }

        public URI getURI()
        {
            return _uri;
        }

        public String getImageURL()
        {
            return _imageURL;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public boolean isDirectory()
        {
            return _isDirectory;
        }

        public String getHref()
        {
            return _href.getLocalURIString();
        }

        public ActionURL cloneHref()
        {
            return _href.clone();
        }

        public FileAction[] getActions()
        {
            return _actions.toArray(new FileAction[0]);
        }

        public void addAction(FileAction action)
        {
            _actions.add(action);
        }

        protected void ensureFiles()
        {
            // See if file list needs initialization.
            if (_files == null)
            {
                _files = new LinkedHashMap<String, File>();
                _fileSets = new HashMap<Class, File[]>();

                if (_isDirectory)
                {
                    // Get the full set of files in the directory.
                    File dir = new File(_uri);
                    File[] files = dir.listFiles();

                    if (files != null)
                    {
                        // Use a file object that caches its directory state.
                        for (int i = 0; i < files.length; i++)
                        {
                            File f = new FileCached(files[i]);
                            _files.put(f.getName(), f);
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

        /**
         * Returns a filtered set of files with cached directory/file status.
         * The function also uses a map to avoid looking for the same fileset
         * multiple times.
         *
         * @param filter The filter to use on the listed files.
         * @return List of filtered files.
         */
        public File[] listFiles(FileFilter filter)
        {
            if (!_isDirectory)
                return new File[0];

            ensureFiles();

            // See if we've already used this filter before.
            // TODO(brendanx): This no longer works with FileAnalysisPipelineProvider
            //                 Saved some time with MS2 providers
//            File[] files = _fileSets.get(filter.getClass());
//            if (files != null)
//                return files;

            // Actually do the filtering.
            ArrayList<File> listFiles = new ArrayList<File>();
            if (filter instanceof FileEntryFilter)
                ((FileEntryFilter)filter).setFileEntry(this);
            for (File f : _files.values())
            {
                if (filter.accept(f))
                    listFiles.add(f);
            }
            File[] files = listFiles.toArray(new File[listFiles.size()]);
            _fileSets.put(filter.getClass(), files);
            return files;
        }

        public void orderActions()
        {
            // Sort each action's array of Files
            for (FileAction a : _actions)
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
            Collections.sort(_actions, new Comparator<FileAction>()
            {
                public int compare(FileAction action1, FileAction action2)
                {
                    int rc;
                    File[] files1 = action1.getFiles();
                    File[] files2 = action2.getFiles();
                    if (files1 == files2)
                        return 0;
                    if (files1 == null)
                        return -1;
                    if (files2 == null)
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

    }

    public static abstract class FileEntryFilter implements FileFilter
    {
        private FileEntry _entry;

        public void setFileEntry(FileEntry entry)
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


    public static class FileAction
    {
        String _description;
        String _label;
        String _href;
        File[] _files;

        public FileAction(String label, ActionURL href, File[] files)
        {
            _label = label;
            _href = href.getLocalURIString();
            _files = files;
        }

        public FileAction(String label, String href, File[] files)
        {
            _label = label;
            _href = href;
            _files = files;
        }

        public String getLabel()
        {
            return _label;
        }

        public String getHref()
        {
            return _href.toString();
        }

        public boolean isRootAction()
        {
            return (_files == null);
        }
        
        public File[] getFiles()
        {
            return _files;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getDescription()
        {
            return _description;
        }

        public boolean hasSameFiles(FileAction action)
        {
            File[] files = action.getFiles();
            if (files == _files)
                return true;
            if (files == null || _files == null)
                return false;
            
            if (files.length != _files.length)
                return false;
            for (int i = 0; i < files.length; i++)
                if (!files[i].getPath().equals(_files[i].getPath()))
                    return false;

            return true;
        }

        public String getDisplay(int i)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("<input type=\"image\" onclick=\"setFormAction(").append(i).append(", '").append(PageFlowUtil.filter(getHref())).append("')\" ")
                    .append("alt=\"").append(PageFlowUtil.filter(getLabel())).append("\" ")
                    .append("src=\"").append(PageFlowUtil.filter(getButtonSrc())).append("\" ")
                    .append("border=\"0\"/>");
            return sb.toString();
        }

        public String getDisplay()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("<a href=\"").append(PageFlowUtil.filter(getHref())).append("\">")
                    .append("<img alt=\"").append(PageFlowUtil.filter(getLabel())).append("\" ")
                    .append("src=\"").append(PageFlowUtil.filter(getButtonSrc())).append("\" ")
                    .append("border=\"0\"/></a>");
            return sb.toString();
        }

        protected String getButtonSrc()
        {
            return PageFlowUtil.buttonSrc(getLabel());
        }
    }

    
    public static class StatusAction
    {
        String _label;
        String _visible;

        public StatusAction(String label)
        {
            this(label, null);
        }

        public StatusAction(String label, String visible)
        {
            this._label = label;
            this._visible = visible;
        }

        public String getLabel()
        {
            return _label;
        }

        public String getVisible()
        {
            return _visible;
        }
    }

    public static class StatusUpdateException extends Exception
    {
        public StatusUpdateException(String message)
        {
            super(message);
        }

        public StatusUpdateException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

    public static class FileCached extends File
    {
        boolean _dir;

        public FileCached(File f)
        {
            super(f.getPath());
            _dir = f.isDirectory();
        }

        public boolean isFile()
        {
            return !_dir;
        }

        public boolean isDirectory()
        {
            return _dir;
        }
    }

    protected String _name;

    public PipelineProvider(String name)
    {
        _name = name;
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
     * Override to do any extra registration work when this provider is
     * registered with the PipelineService, like registering default
     * <code>TaskPipeline</code> instances.
     */
    public void register()
    {
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
     * @param name the file name
     * @param basename the base name associated with the status
     * @return true if link should be displayed
     */
    public boolean isStatusViewableFile(String name, String basename)
    {
        return PipelineJob.FT_LOG.isMatch(name, basename);
    }

    /**
     * Override to do any extra work necessary before deleting a status entry.
     *
     * @param sf the entry to delete
     */
    public void preDeleteStatusFile(PipelineStatusFile sf) throws StatusUpdateException
    {
    }

    /**
     * Override to do any extra work necessary before completing a status entry.
     *
     * @param sf the entry to delete
     */
    public void preCompleteStatusFile(PipelineStatusFile sf) throws StatusUpdateException
    {
    }

    /**
     * @return Web part shown on the setup page.
     */
    public HttpView getSetupWebPart()
    {
        // No setup.
        return null;
    }

    /**
     * Allows the provider to add actions to files in the current directory
     * during pipeline root navigation by the user.
     *
     * @param context The ViewContext for the current request
     * @param entries List of directories to scan for possible actions
     */
    public void updateFileProperties(ViewContext context, List<FileEntry> entries)
    {
        // Do nothing.
    }

    /**
     * Allows the provider to add action buttons to the details page of one
     * of its status entries.
     *
     * @return List of actions to add to the details page
     */
    public List<StatusAction> addStatusActions()
    {
        // No extra actions.
        return null;
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
        // No actions to handle.
        return null;
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

    protected void addAction(ActionURL actionURL, String description, FileEntry entry, File[] files)
    {
        if (files == null || files.length == 0)
            return;

        entry.addAction(new FileAction(description, actionURL, files));
    }

    protected void addAction(String pageflow, String action, String description, FileEntry entry, File[] files)
    {
        if (files == null || files.length == 0)
            return;
        ActionURL actionURL = entry.cloneHref();
        actionURL.setPageFlow(pageflow);
        actionURL.setAction(action);
        entry.addAction(new FileAction(description, actionURL, files));
    }

    protected void addFileActions(String pageflow, String action, String description, FileEntry entry, File[] files)
    {
        if (files == null || files.length == 0)
            return;
        ActionURL actionURL = entry.cloneHref();
        actionURL.setPageFlow(pageflow);
        actionURL.setAction(action);
        String path = actionURL.getParameter(Params.path);
        for (File f : files)
        {
            actionURL.replaceParameter(Params.path, path + PageFlowUtil.encode(f.getName()));
            entry.addAction(new FileAction(description, actionURL.clone(), new File[] {f}));
        }
    }

    /**
     * Return true if this provider believes that it is in use in the container, and
     * also can handle overlapping pipeline roots.
     * A pipeline provider should not return "true" without first checking that it is active (i.e. that the
     * current container's folder type is one that makes use of this pipeline provider.
     * Many modules (e.g. MS2) have trouble with overlapping pipeline roots.  It is important that in MS2 folders
     * the user be shown the warning.
     * @return
     */
    public boolean suppressOverlappingRootsWarning(ViewContext context)
    {
        return false;
    }
}
