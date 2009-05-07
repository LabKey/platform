package org.labkey.study.writer;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.AdditiveType;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:51:36 AM
 */
public class AdditiveTypeWriter implements Writer<AdditiveType[]>
{
    public void write(AdditiveType[] types, ExportContext ctx, VirtualFile fs) throws Exception
    {
        PrintWriter pw = fs.getPrintWriter("additives.tsv");

        pw.println("# additives");
        pw.println("additive_id\tldms_additive_code\tlabware_additive_code\tadditive");

        for (AdditiveType type : types)
        {
            pw.print(String.valueOf(type.getExternalId()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getLdmsAdditiveCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getLabwareAdditiveCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getAdditive()));
            pw.println();
        }

        pw.close();
    }
}