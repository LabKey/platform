package org.labkey.api.reports.report.r;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.reports.Report;

import java.io.File;

/**
 * Handles substitution parameters for R reports by mapping symbolic names to
 * files on generation, as well as rendering the results.
 *
 * User: Karl Lum
 * Date: May 5, 2008
 */
public interface ParamReplacement
{
    /** unique identifier for this replacement type */
    public String getId();

    /** the name portion of the output replacement, must be unique within the R script */
    public String getName();
    public void setName(String name);

    /**
     * Convert the substitution to it's eventual generated file.
     * @param directory - the parent directory to crete the generated file (if any, can be null)
     */
    public File convertSubstitution(File directory) throws Exception;

    public File getFile();
    public void setFile(File file);
    public String toString();
    
    public void setReport(Report report);
    public Report getReport();

    public void setHeaderVisible(boolean visible);
    public boolean getHeaderVisible();

    public HttpView render(ViewContext context);
}
