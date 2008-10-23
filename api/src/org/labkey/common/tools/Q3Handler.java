/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.apache.log4j.Logger;

import javax.xml.stream.XMLStreamException;

/**
 * User: jeckels
 * Date: Dec 14, 2006
 */
public class Q3Handler extends PepXmlAnalysisResultHandler
{
    public static final String analysisType = "q3";

    static Logger _log = Logger.getLogger(PepXmlAnalysisResult.class);

    protected Q3Result getResult(SimpleXMLStreamReader parser) throws XMLStreamException
    {
        parser.skipToStart("q3ratio_result");
        Q3Result result = new Q3Result();

        result.setDecimalRatio(Float.parseFloat(parser.getAttributeValue(null, "decimal_ratio")));
        result.setHeavyArea(Float.parseFloat(parser.getAttributeValue(null, "heavy_area")));
        result.setHeavyFirstscan(Integer.parseInt(parser.getAttributeValue(null, "heavy_firstscan")));
        result.setHeavyLastscan(Integer.parseInt(parser.getAttributeValue(null, "heavy_lastscan")));
        result.setHeavyMass(Float.parseFloat(parser.getAttributeValue(null, "heavy_mass")));
        result.setLightArea(Float.parseFloat(parser.getAttributeValue(null, "light_area")));
        result.setLightFirstscan(Integer.parseInt(parser.getAttributeValue(null, "light_firstscan")));
        result.setLightLastscan(Integer.parseInt(parser.getAttributeValue(null, "light_lastscan")));
        result.setLightMass(Float.parseFloat(parser.getAttributeValue(null, "light_mass")));
        return result;
    }

    public String getAnalysisType()
    {
        return analysisType;
    }

    public static class Q3Result extends RelativeQuantAnalysisResult
    {
        public String getAnalysisType()
        {
            return analysisType;
        }
    }
}
