package org.labkey.study.writer;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.DerivativeType;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:51:36 AM
 */
public class DerivativeTypeWriter implements Writer<DerivativeType[]>
{
    public void write(DerivativeType[] types, ExportContext ctx, VirtualFile fs) throws Exception
    {
        PrintWriter pw = fs.getPrintWriter("derivatives.tsv");

        pw.println("# derivatives");
        pw.println("derivative_id\tldms_derivative_code\tlabware_derivative_code\tderivative");

        for (DerivativeType type : types)
        {
            pw.print(String.valueOf(type.getExternalId()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getLdmsDerivativeCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getLabwareDerivativeCode()) + '\t');
            pw.print(StringUtils.trimToEmpty(type.getDerivative()));
            pw.println();
        }

        pw.close();
    }
}