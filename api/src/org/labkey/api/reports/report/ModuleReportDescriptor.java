package org.labkey.api.reports.report;

import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

/**
 * User: klum
 * Date: 9/6/13
 */
public interface ModuleReportDescriptor
{
    /**
     * Returns the module that this report is contained in
     */
    Module getModule();

    /**
     * Get the path to the report resource
     */
    Path getReportPath();
    Resource getSourceFile();
}
