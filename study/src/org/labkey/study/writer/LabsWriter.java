package org.labkey.study.writer;

import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.Site;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:51:36 AM
 */
public class LabsWriter implements Writer<Site[]>
{
    public void write(Site[] sites, ExportContext ctx, VirtualFile fs) throws Exception
    {
        PrintWriter pw = fs.getPrintWriter("labs.tsv");

        for (Site site : sites)
        {
            site.getExternalId();
            site.getLdmsLabCode();
            site.getLabel();
            site.getTypeString();
        }

        pw.close();
    }
}
