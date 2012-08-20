package org.labkey.api.reports.report;

import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

/**
 * User: Nick
 * Date: 8/20/12
 */
public class ModuleQueryJavaScriptReportDescriptor extends ModuleJavaScriptReportDescriptor
{
    public ModuleQueryJavaScriptReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath)
    {
        super(module, reportKey, sourceFile, reportPath);

        if (null == getProperty(ReportDescriptor.Prop.schemaName))
        {
            //key is <schema-name>/<query-name>
            String[] keyParts = reportKey.split("/");
            if (keyParts.length >= 2)
            {
                setProperty(ReportDescriptor.Prop.schemaName, keyParts[0]);
                setProperty(ReportDescriptor.Prop.queryName, keyParts[1]);
            }
        }
    }


}
