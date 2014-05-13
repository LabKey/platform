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

package org.labkey.study.importer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.VisitTag;
import org.labkey.study.xml.DatasetType;
import org.labkey.study.xml.VisitMapDocument;
import org.labkey.study.xml.VisitMapDocument.VisitMap.ImportAliases;
import org.labkey.study.xml.VisitMapDocument.VisitMap.ImportAliases.Alias;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: May 20, 2009
 * Time: 5:07:42 PM
 */
public class XmlVisitMapReader implements VisitMapReader
{
    private final VisitMapDocument.VisitMap _visitMapXml;

    public XmlVisitMapReader(String xml) throws VisitMapParseException
    {
        try
        {
            VisitMapDocument doc = VisitMapDocument.Factory.parse(xml, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(doc);
            _visitMapXml = doc.getVisitMap();
        }
        catch (XmlException x)
        {
            // TODO: Use InvalidFileException... but need to pass in root and file instead of an xml string
            XmlError error = x.getError();
            throw new VisitMapParseException("visit map XML file is not valid: " + error.getLine() + ":" + error.getColumn() + ": " + error.getMessage());
        }
        catch (XmlValidationException e)
        {
            throw new VisitMapParseException("visit map XML file is not valid: it does not conform to visitMap.xsd", e);
        }
    }

    public XmlVisitMapReader(XmlObject xmlObj) throws VisitMapParseException
    {
        try
        {
            if (xmlObj instanceof VisitMapDocument)
            {
                VisitMapDocument doc = (VisitMapDocument)xmlObj;
                XmlBeansUtil.validateXmlDocument(doc);
                _visitMapXml = doc.getVisitMap();
            }
            else
                throw new VisitMapParseException("The XmlObject specified is not an instance of a VisitMapDocument");
        }
        catch (XmlValidationException e)
        {
            throw new VisitMapParseException("visit map XML file is not valid: it does not conform to visitMap.xsd", e);
        }
    }

    @Override
    @NotNull
    public List<VisitMapRecord> getVisitMapRecords(TimepointType timepointType) throws VisitMapParseException
    {
        VisitMapDocument.VisitMap.Visit[] visitsXml = _visitMapXml.getVisitArray();
        List<VisitMapRecord> visits = new ArrayList<>(visitsXml.length);

        for (VisitMapDocument.VisitMap.Visit visitXml : visitsXml)
        {
            double maxSequenceNum = visitXml.isSetMaxSequenceNum() ? visitXml.getMaxSequenceNum() : visitXml.getSequenceNum();
            Double protocolDay = null;
            if (visitXml.isSetProtocolDay())
                protocolDay = visitXml.getProtocolDay();
            else if (TimepointType.DATE == timepointType)
                VisitImpl.calcDefaultDateBasedProtocolDay(visitXml.getSequenceNum(), maxSequenceNum);

            List<Integer> required = new ArrayList<>();
            List<Integer> optional = new ArrayList<>();

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

            VisitMapRecord record = new VisitMapRecord(visitXml.getSequenceNum(), maxSequenceNum, protocolDay, visitXml.getTypeCode(),
                    visitXml.getLabel(), visitXml.getDescription(), visitXml.getCohort(), visitXml.getVisitDateDatasetId(),
                    ArrayUtils.toPrimitive(required.toArray(new Integer[required.size()])),
                    ArrayUtils.toPrimitive(optional.toArray(new Integer[optional.size()])), visitXml.getShowByDefault(),
                    visitXml.getDisplayOrder(), visitXml.getChronologicalOrder(), visitXml.getSequenceNumHandling(),
                    getVisitTagRecords(visitXml));

            visits.add(record);
        }

        return visits;
    }


    @Override
    @NotNull
    public List<StudyManager.VisitAlias> getVisitImportAliases()
    {
        List<StudyManager.VisitAlias> ret = new LinkedList<>();
        ImportAliases importAliasesXml = _visitMapXml.getImportAliases();

        if (null != importAliasesXml)
        {
            Alias[] aliases = importAliasesXml.getAliasArray();

            for (Alias alias : aliases)
                ret.add(new StudyManager.VisitAlias(alias.getName(), alias.getSequenceNum()));
        }

        return ret;
    }

    @Override
    @NotNull
    public List<VisitTag> getVisitTags() throws VisitMapParseException
    {
        VisitMapDocument.VisitMap.VisitTag[] visitTagsXml = _visitMapXml.getVisitTagArray();
        List<VisitTag> visitTags = new ArrayList<>(visitTagsXml.length);

        for (VisitMapDocument.VisitMap.VisitTag visitTagXml : visitTagsXml)
        {
            VisitTag visitTag = new VisitTag(visitTagXml.getName(), visitTagXml.getCaption(),
                                             visitTagXml.getDescription(), visitTagXml.getSingleUse());
            visitTags.add(visitTag);
        }

        return visitTags;
    }

    private List<VisitMapRecord.VisitTagRecord> getVisitTagRecords(VisitMapDocument.VisitMap.Visit visitXml)
    {
        List<VisitMapRecord.VisitTagRecord> visitTagRecords = new ArrayList<>();
        if (null != visitXml.getVisitTags())
            for (VisitMapDocument.VisitMap.Visit.VisitTags.VisitTag visitTag : visitXml.getVisitTags().getVisitTagArray())
                visitTagRecords.add(new VisitMapRecord.VisitTagRecord(visitTag.getName(), visitTag.getCohort()));

        return visitTagRecords;
    }
}
