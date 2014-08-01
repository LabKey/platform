/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.labkey.api.admin.ImportException;
import org.labkey.api.study.Visit;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.VisitDataSet;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.xml.StudyDocument;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:57:56 AM
 */
public class DataFaxVisitMapWriter implements Writer<StudyImpl, StudyExportContext>
{
    public static final String FILENAME = "visit_map.txt";

    public String getSelectionText()
    {
        return null;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws IOException, ImportException
    {
        List<VisitImpl> visits = study.getVisits(Visit.Order.DISPLAY);
        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Visits visitsXml = studyXml.addNewVisits();
        visitsXml.setFile(FILENAME);

        try (PrintWriter out = vf.getPrintWriter(FILENAME))
        {
            NumberFormat df = new DecimalFormat("#.#####");

            for (VisitImpl v : visits)
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

                // TODO: out.print(v.getVisitDateDatasetId())

                out.print("|||||");

                String s = "";

                // Required datasets
                for (VisitDataSet vd : vds)
                {
                    if (vd.isRequired() && ctx.isExportedDataset(vd.getDataSetId()))
                    {
                        out.print(s);
                        out.print(vd.getDataSetId());
                        s = " ";
                    }
                }

                out.print("|");
                s = "";

                // Optional datasets
                for (VisitDataSet vd : vds)
                {
                    if (!vd.isRequired())
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
}
