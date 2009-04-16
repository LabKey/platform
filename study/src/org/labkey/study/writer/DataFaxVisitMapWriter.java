/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.writer;

import org.labkey.study.model.Study;
import org.labkey.study.model.Visit;
import org.labkey.study.model.VisitDataSet;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:57:56 AM
 */
public class DataFaxVisitMapWriter extends VisitMapWriter
{
    protected DataFaxVisitMapWriter(Study study, File dir)
    {
        super(study, new File(dir, "visit_map.txt"));
    }

    protected void write(Visit[] visits, PrintWriter out)
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
}
