package org.labkey.study.writer;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.PrimaryType;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:51:36 AM
 */
public class PrimaryTypeWriter implements Writer<PrimaryType[]>
{
    public void write(PrimaryType[] types, ExportContext ctx, VirtualFile fs) throws Exception
    {
        PrintWriter pw = fs.getPrintWriter("primary_types.tsv");

        pw.println("# primary_types");
        pw.println("primary_type_id\tprimary_type_ldms_code\tprimary_type_labware_code\tprimary_type");

        for (PrimaryType type : types)
        {
            pw.print(String.valueOf(type.getExternalId()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getPrimaryTypeLDMSCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getPrimaryTypeLabwareCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getPrimaryType()));
            pw.println();
        }

        pw.close();
    }
}