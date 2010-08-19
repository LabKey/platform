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
