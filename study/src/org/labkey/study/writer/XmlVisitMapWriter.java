/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitDataset;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.VisitTag;
import org.labkey.study.model.VisitTagMapEntry;
import org.labkey.study.xml.DatasetType;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.VisitMapDocument;
import org.labkey.study.xml.VisitMapDocument.VisitMap;
import org.labkey.study.xml.VisitMapDocument.VisitMap.ImportAliases;
import org.labkey.study.xml.VisitMapDocument.VisitMap.ImportAliases.Alias;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 15, 2009
 * Time: 10:57:56 AM
 */
public class XmlVisitMapWriter implements Writer<StudyImpl, StudyExportContext>
{
    public static final String FILENAME = "visit_map.xml";

    public String getDataType()
    {
        return null;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws IOException, ImportException, SQLException
    {
        List<VisitImpl> visits = study.getVisits(Visit.Order.DISPLAY);
        Map<Integer, List<VisitTagMapEntry>> visitTagMapMap = StudyManager.getInstance().getVisitTagMapMap(study);

        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Visits visitsXml = studyXml.addNewVisits();
        visitsXml.setFile(FILENAME);

        VisitMapDocument visitMapDoc = VisitMapDocument.Factory.newInstance();
        VisitMap visitMapXml = visitMapDoc.addNewVisitMap();

        Set<Integer> visitsToExport = ctx.getVisitIds();

        for (VisitImpl visit : visits)
        {
            if (visitsToExport == null || visitsToExport.contains(visit.getId()))
            {
                VisitMap.Visit visitXml = visitMapXml.addNewVisit();

                if (null != visit.getLabel())
                    visitXml.setLabel(visit.getLabel());

                if (null != visit.getDescription())
                    visitXml.setDescription(visit.getDescription());

                if (null != visit.getTypeCode())
                    visitXml.setTypeCode(String.valueOf(visit.getTypeCode()));

                // Only set if false; default value is "true"
                if (!visit.isShowByDefault())
                    visitXml.setShowByDefault(visit.isShowByDefault());

                visitXml.setSequenceNum(visit.getSequenceNumMin());

                if (visit.getSequenceNumMin() != visit.getSequenceNumMax())
                    visitXml.setMaxSequenceNum(visit.getSequenceNumMax());

                if (null != visit.getProtocolDay())
                    visitXml.setProtocolDay(visit.getProtocolDay());

                if (null != visit.getCohort())
                    visitXml.setCohort(visit.getCohort().getLabel());

                if (null != visit.getVisitDateDatasetId() && ctx.isExportedDataset(visit.getVisitDateDatasetId()))
                    visitXml.setVisitDateDatasetId(visit.getVisitDateDatasetId());

                if (visit.getDisplayOrder() > 0)
                    visitXml.setDisplayOrder(visit.getDisplayOrder());

                if (visit.getChronologicalOrder() > 0)
                    visitXml.setChronologicalOrder(visit.getChronologicalOrder());

                if (visit.getSequenceNumHandlingEnum() != Visit.SequenceHandling.normal)
                    visitXml.setSequenceNumHandling(visit.getSequenceNumHandling());

                List<VisitDataset> vds = visit.getVisitDatasets();

                if (!vds.isEmpty())
                {
                    VisitMap.Visit.Datasets datasetsXml = visitXml.addNewDatasets();

                    for (VisitDataset vd : vds)
                    {
                        if (ctx.isExportedDataset(vd.getDatasetId()))
                        {
                            VisitMap.Visit.Datasets.Dataset datasetXml = datasetsXml.addNewDataset();
                            datasetXml.setId(vd.getDatasetId());
                            datasetXml.setType(vd.isRequired() ? DatasetType.REQUIRED : DatasetType.OPTIONAL);
                        }
                    }
                }

                if (visitTagMapMap.containsKey(visit.getRowId()))
                {
                    VisitMap.Visit.VisitTags visitTagsXml = visitXml.addNewVisitTags();

                    for (VisitTagMapEntry visitTagMapEntry : visitTagMapMap.get(visit.getRowId()))
                    {
                        VisitMap.Visit.VisitTags.VisitTag visitTagXml = visitTagsXml.addNewVisitTag();
                        visitTagXml.setName(visitTagMapEntry.getVisitTag());
                        if (null != visitTagMapEntry.getCohortId())
                        {
                            visitTagXml.setCohort(StudyManager.getInstance().getCohortForRowId(study.getContainer(),
                                                    ctx.getUser(), visitTagMapEntry.getCohortId()).getLabel());
                        }
                    }
                }
            }
        }

        Collection<StudyManager.VisitAlias> aliases = StudyManager.getInstance().getCustomVisitImportMapping(study);

        if (!aliases.isEmpty())
        {
            ImportAliases ia = visitMapXml.addNewImportAliases();

            for (StudyManager.VisitAlias alias : aliases)
            {
                Alias aliasXml = ia.addNewAlias();
                aliasXml.setName(alias.getName());
                aliasXml.setSequenceNum(alias.getSequenceNum());
            }
        }

        // In Dataspace, VisitTags live at the project level
        Study studyForVisitTags = StudyManager.getInstance().getStudyForVisitTag(study);
        Collection<VisitTag> visitTags = StudyManager.getInstance().getVisitTags(studyForVisitTags).values();

        for (VisitTag visitTag : visitTags)
        {
            VisitMap.VisitTag visitTagXml = visitMapXml.addNewVisitTag();
            visitTagXml.setName(visitTag.getName());
            visitTagXml.setCaption(visitTag.getCaption());
            if (null != visitTag.getDescription())
                visitTagXml.setDescription(visitTag.getDescription());
            visitTagXml.setSingleUse(visitTag.isSingleUse());
        }

        vf.saveXmlBean(FILENAME, visitMapDoc);
    }
}
