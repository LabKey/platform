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
package org.labkey.study.importer;

import org.labkey.study.xml.VisitMapDocument;
import org.labkey.study.xml.DatasetType;
import org.labkey.api.study.StudyImportException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlError;

import java.util.List;
import java.util.ArrayList;

/**
 * User: adam
 * Date: May 20, 2009
 * Time: 5:07:42 PM
 */
public class XmlVisitMapReader implements VisitMapReader
{
    public List<VisitMapRecord> getRecords(String xml) throws StudyImportException
    {
        VisitMapDocument doc;

        try
        {
            doc = VisitMapDocument.Factory.parse(xml);
        }
        catch (XmlException x)
        {
            // TODO: Use InvalidFileException... but need to pass in root and file instead of an xml string
            XmlError error = x.getError();
            throw new StudyImportException("visit_map.xml is not valid: " + error.getLine() + ":" + error.getColumn() + ": " + error.getMessage());
        }

        VisitMapDocument.VisitMap.Visit[] visitsXml = doc.getVisitMap().getVisitArray();
        List<VisitMapRecord> visits = new ArrayList<VisitMapRecord>(visitsXml.length);

        for (VisitMapDocument.VisitMap.Visit visitXml : visitsXml)
        {
            double maxSequenceNum = visitXml.isSetMaxSequenceNum() ? visitXml.getMaxSequenceNum() : visitXml.getSequenceNum();

            List<Integer> required = new ArrayList<Integer>();
            List<Integer> optional = new ArrayList<Integer>();

            if (null != visitXml.getDatasets())
            {
                for (VisitMapDocument.VisitMap.Visit.Datasets.Dataset dataset : visitXml.getDatasets().getDatasetArray())
                {
                    if (dataset.getType() == DatasetType.REQUIRED)
                        required.add(dataset.getId());
                    else
                        optional.add(dataset.getId());
                }
            }

            VisitMapRecord record = new VisitMapRecord(visitXml.getSequenceNum(), maxSequenceNum, visitXml.getTypeCode(),
                    visitXml.getLabel(), visitXml.getCohort(), visitXml.getVisitDateDatasetId(),
                    ArrayUtils.toPrimitive(required.toArray(new Integer[required.size()])),
                    ArrayUtils.toPrimitive(optional.toArray(new Integer[optional.size()])), visitXml.getShowByDefault(),
                    visitXml.getDisplayOrder(), visitXml.getChronologicalOrder());

            visits.add(record);
        }

        return visits;
    }
}
