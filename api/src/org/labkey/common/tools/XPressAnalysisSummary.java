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

import net.systemsbiology.regisWeb.pepXML.XpressratioSummaryDocument;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;

import javax.xml.stream.XMLStreamException;

/**
 * Represent an XPRESS analysis summary from a pepXML file
 */
public class XPressAnalysisSummary extends RelativeQuantAnalysisSummary
{
    private static Logger _log = Logger.getLogger(XPressAnalysisSummary.class);

    public static final String analysisType = "xpress";

    private String _sameScanRange;
    private long _xpressLight;

    public static XPressAnalysisSummary load(SimpleXMLStreamReader parser) throws XMLStreamException
    {
        // We must be on the analysis_summary start element when called.
        String analysisTime = parser.getAttributeValue(null, "time");

        if (!parser.skipToStart("xpressratio_summary"))
            throw new XMLStreamException("Did not find required xpressratio_summary tag in analysis result");

        XpressratioSummaryDocument summaryDoc;

        try
        {
            summaryDoc = XpressratioSummaryDocument.Factory.parse(parser);
        }
        catch(XmlException e)
        {
            throw new XMLStreamException("Parsing XPRESS summary", e);
        }

        XpressratioSummaryDocument.XpressratioSummary summary = summaryDoc.getXpressratioSummary();

        XPressAnalysisSummary analysisSummary = new XPressAnalysisSummary(summary);
        if (null != analysisTime)
            analysisSummary.setAnalysisTime(SimpleXMLEventRewriter.convertXMLTimeToDate(analysisTime));
        return analysisSummary;
    }

    /**
     * No argument constructor required for BeanObjectFactory...
     */
    public XPressAnalysisSummary()
    {
        super(analysisType);
    }

    private XPressAnalysisSummary(XpressratioSummaryDocument.XpressratioSummary summary)
    {
        super(analysisType);
	setVersion(summary.getVersion());
	setLabeledResidues(summary.getLabeledResidues());
	setMassDiff(summary.getMassdiff());
	setMassTol(summary.getMasstol());
	_sameScanRange = summary.getSameScanRange();
	_xpressLight = summary.getXpressLight();
    }

    /**
     * Disallowed; can't change analysis type
     */
    public void setAnalysisType(String analysisType)
    {
        throw new UnsupportedOperationException("Can not change analysis type of an XPress summary");
    }

    public String getSameScanRange()
    {
	return _sameScanRange;
    }

    public void setSameScanRange(String sameScanRange)
    {
	_sameScanRange = sameScanRange;
    }

    public long getXpressLight()
    {
	return _xpressLight;
    }

    public void setXpressLight(long xpressLight)
    {
	_xpressLight = xpressLight;
    }

}
