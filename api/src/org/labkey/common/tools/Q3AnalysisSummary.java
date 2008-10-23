/*
 * Copyright (c) 2006-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.common.tools;

import org.labkey.common.tools.SimpleXMLEventRewriter;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;

import javax.xml.stream.XMLStreamException;

/**
 * Represent a q3 analysis summary loaded from pepXML
 */
public class Q3AnalysisSummary extends RelativeQuantAnalysisSummary
{
    private static Logger _log = Logger.getLogger(Q3AnalysisSummary.class);
    
    private static final float DEFAULT_MASSTOL = .1f;

    public static final String analysisType = "q3";

    /**
     *
     */
    public static Q3AnalysisSummary load(SimpleXMLStreamReader parser) throws XMLStreamException
    {
        // We must be on the analysis_summary start element when called.
        String analysisTime = parser.getAttributeValue(null, "time");

        if (!parser.skipToStart("q3ratio_summary"))
            throw new XMLStreamException("Did not find required q3ratio_summary tag in analysis result");

        Q3AnalysisSummary summary = new Q3AnalysisSummary();

        summary.setVersion(parser.getAttributeValue(null, "version"));
        summary.setLabeledResidues(parser.getAttributeValue(null, "labeled_residues"));
        summary.setMassDiff(parser.getAttributeValue(null, "massdiff"));
        summary.setMassTol(parseMassTol(parser.getAttributeValue(null, "masstol")));

        if (null != analysisTime)
            summary.setAnalysisTime(SimpleXMLEventRewriter.convertXMLTimeToDate(analysisTime));

        return summary;
    }

    /**
     * No arg constructor for BeanObjectFactory
     */
    public Q3AnalysisSummary()
    {
        super(analysisType);
    }

    /**
     * Disallowed; can't change analysis type
     */
    public void setAnalysisType(String analysisType)
    {
        throw new UnsupportedOperationException("Can not change analysis type of an Q3 summary");
    }

    /**
     *
     */
    private static float parseMassTol(String massTol)
    {
        try
        {
            if (null != massTol)
                return Float.parseFloat(massTol);
        }
        catch (NumberFormatException e)
        {
            _log.warn("Error parsing mass tolerance " + massTol + "; using default");
        }
        return DEFAULT_MASSTOL;
    }

}
