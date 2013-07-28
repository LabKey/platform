package org.labkey.visualization;

import org.apache.commons.collections15.MultiMap;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.UserManager;
import org.labkey.api.visualization.GenericChartReportDescriptor;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;


public class VisualizationUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(VisualizationUpgradeCode.class);

    // Called at 13.20 -> 13.21
    public void upgradeGenericChartSaveConfig(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        MultiMap<Container, Container> containerTree = ContainerManager.getContainerTree();
        for (Map.Entry<Container, Collection<Container>> treeEntry : containerTree.entrySet())
        {
            for (Container container : treeEntry.getValue())
            {
                Report[] reports = ReportService.get().getReports(null, container);
                if (reports.length > 0)
                {
                    for (Report report : reports)
                    {
                        if (report.getDescriptor().getDescriptorType().equals(GenericChartReportDescriptor.TYPE))
                        {
                            GenericChartReportDescriptor descriptor = (GenericChartReportDescriptor) report.getDescriptor();
                            descriptor.updateSaveConfig();
                            ContainerUser rptContext = new DefaultContainerUser(container, UserManager.getUser(descriptor.getModifiedBy()));
                            try
                            {
                                ReportService.get().saveReport(rptContext, descriptor.getReportKey(), report, true);
                            }
                            catch (SQLException e)
                            {
                                _log.error("An error occurred upgrading generic chart report properties: ", e);
                            }
                        }
                    }
                }
            }
        }
    }
}
