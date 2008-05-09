package org.labkey.api.reports.report;

import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Aug 12, 2007
 */

/**
 * Represents an object which can process R scripts and return results
 */
public interface RScriptRunner
{
    public void setReport(RReport report);
    public void setViewContext(ViewContext context);
    public void setSourceData(File data);

    /**
     * Specify whether temporary files should be deleted when the
     * view is rendered.
     * @param deleteTempFiles
     */
    public void setDeleteTempFiles(boolean deleteTempFiles);

    /**
     * Execute the script and return the list of output file mappings.
     * @param outputSubstitutions : the mapping of generated output files to view type id's in
     * which to display them.
     * @return
     * @throws Exception
     */
    public boolean runScript(VBox view, List<ParamReplacement> outputSubstitutions);
}

