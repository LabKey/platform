package org.labkey.study.writer;

import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.Visit;
import org.labkey.study.model.VisitDataSet;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

/**
 * User: adam
 * Date: Apr 21, 2009
 * Time: 8:26:54 PM
 */
public class TdmVisitMapWriter implements Writer<Visit[]>
{
    public void write(Visit[] visits, ExportContext ctx, VirtualFile fs) throws Exception
    {
        PrintWriter out = fs.getPrintWriter("visit_map.xml");

        try
        {
            NumberFormat df = new DecimalFormat("#.#####");

            for (Visit v : visits)
            {
                List<VisitDataSet> vds = v.getVisitDataSets();

                out.print(df.format(v.getSequenceNumMin()));

                if (v.getSequenceNumMin() != v.getSequenceNumMax())
                {
                    out.print("-");
                    out.print(df.format(v.getSequenceNumMax()));
                }

                out.print('|');

                if (null != v.getTypeCode())
                    out.print(v.getTypeCode());

                out.print('|');

                if (null != v.getLabel())
                    out.print(v.getLabel());

                out.printf("|||||");

                String s = "";

                for (VisitDataSet vd : vds)
                {
                    if (vd.isRequired())
                    {
                        out.print(s);
                        out.print(vd.getDataSetId());
                        s = " ";
                    }
                }

                out.print("|");

                for (VisitDataSet vd : vds)
                {
                    if (vd.isRequired())
                    {
                        out.print(s);
                        out.print(vd.getDataSetId());
                        s = " ";
                    }
                }

                out.println("||");
            }
        }
        finally
        {
            if (null != out)
                out.close();
        }
    }
}
