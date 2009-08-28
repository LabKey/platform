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

import org.labkey.api.study.StudyImportException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.model.VisitDataSet;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.xml.DatasetType;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.VisitMapDocument;

import java.io.IOException;
import java.util.List;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:57:56 AM
 */
public class XmlVisitMapWriter implements Writer<VisitImpl[], StudyExportContext>
{
    public static final String FILENAME = "visit_map.xml";

    public String getSelectionText()
    {
        return null;
    }

    public void write(VisitImpl[] visits, StudyExportContext ctx, VirtualFile vf) throws IOException, StudyImportException
    {
        StudyDocument.Study studyXml = ctx.getStudyXml();
        StudyDocument.Study.Visits visitsXml = studyXml.addNewVisits();
        visitsXml.setFile(FILENAME);

        VisitMapDocument visitMapDoc = VisitMapDocument.Factory.newInstance();
        VisitMapDocument.VisitMap visitMapXml = visitMapDoc.addNewVisitMap();

        for (VisitImpl visit : visits)
        {
            VisitMapDocument.VisitMap.Visit visitXml = visitMapXml.addNewVisit();

            if (null != visit.getLabel())
                visitXml.setLabel(visit.getLabel());

            if (null != visit.getTypeCode())
                visitXml.setTypeCode(String.valueOf(visit.getTypeCode()));

            // Only set if false; default value is "true"
            if (!visit.isShowByDefault())
                visitXml.setShowByDefault(visit.isShowByDefault());

            visitXml.setSequenceNum(visit.getSequenceNumMin());

            if (visit.getSequenceNumMin() != visit.getSequenceNumMax())
                visitXml.setMaxSequenceNum(visit.getSequenceNumMax());

            if (null != visit.getCohort())
                visitXml.setCohort(visit.getCohort().getLabel());

            if (null != visit.getVisitDateDatasetId() && ctx.isExportedDataset(visit.getVisitDateDatasetId()))
                visitXml.setVisitDateDatasetId(visit.getVisitDateDatasetId().intValue());

            List<VisitDataSet> vds = visit.getVisitDataSets();

            if (!vds.isEmpty())
            {
                VisitMapDocument.VisitMap.Visit.Datasets datasetsXml = visitXml.addNewDatasets();

                for (VisitDataSet vd : vds)
                {
                    if (ctx.isExportedDataset(vd.getDataSetId()))
                    {
                        VisitMapDocument.VisitMap.Visit.Datasets.Dataset datasetXml = datasetsXml.addNewDataset();
                        datasetXml.setId(vd.getDataSetId());
                        datasetXml.setType(vd.isRequired() ? DatasetType.REQUIRED : DatasetType.OPTIONAL);
                    }
                }
            }
        }

        XmlBeansUtil.saveDoc(vf.getPrintWriter(FILENAME), visitMapDoc);
    }
}
