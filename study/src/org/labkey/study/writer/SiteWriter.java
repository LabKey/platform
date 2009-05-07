package org.labkey.study.writer;

import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.Site;
import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:51:36 AM
 */
public class SiteWriter implements Writer<Site[]>
{
    public void write(Site[] sites, ExportContext ctx, VirtualFile fs) throws Exception
    {
        // Make a copy so we can modify the array
        Site[] copy = new Site[sites.length];
        System.arraycopy(sites, 0, copy, 0, sites.length);
        Arrays.sort(copy, new Comparator<Site>(){
            public int compare(Site s1, Site s2)
            {
                return s1.getExternalId().compareTo(s2.getExternalId());
            }
        });

        PrintWriter pw = fs.getPrintWriter("labs.tsv");

        pw.println("# labs");
        pw.println("lab_id\tldms_lab_code\tlabware_lab_code\tlab_name\tlab_upload_code\tis_sal\tis_repository\tis_endpoint");

        for (Site site : copy)
        {
            pw.print(String.valueOf(site.getExternalId()) + '\t');
            pw.print(String.valueOf(site.getLdmsLabCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(site.getLabwareLabCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(site.getLabel()) + '\t');
            pw.print(StringUtils.trimToEmpty(site.getLabUploadCode()));
            outputBit(pw, site.isSal());
            outputBit(pw, site.isRepository());
            outputBit(pw, site.isEndpoint());
            outputBit(pw, site.isClinic());
            pw.println();
        }

        pw.close();
    }

    private void outputBit(PrintWriter pw, Boolean bit)
    {
        pw.print("\t" + (null == bit || !bit.booleanValue() ? "" : "0"));
    }
}
