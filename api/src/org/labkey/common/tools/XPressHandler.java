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

import javax.xml.stream.XMLStreamException;

/**
 * User: arauch
 * Date: Feb 16, 2006
 * Time: 1:53:08 PM
 */
public class XPressHandler extends PepXmlAnalysisResultHandler
{
    public final static String analysisType = "xpress";

    protected XPressResult getResult(SimpleXMLStreamReader parser) throws XMLStreamException
    {
        parser.skipToStart("xpressratio_result");
        XPressResult result = new XPressResult();

        //"decimal_ratio" is a field known to have "inf" as a value representing infinity sometimes
        result.setDecimalRatio(parseFloatHandleInf(parser.getAttributeValue(null, "decimal_ratio")));
        result.setHeavy2lightRatio(parser.getAttributeValue(null, "heavy2light_ratio"));
        result.setHeavyArea(Float.parseFloat(parser.getAttributeValue(null, "heavy_area")));
        result.setHeavyFirstscan(Integer.parseInt(parser.getAttributeValue(null, "heavy_firstscan")));
        result.setHeavyLastscan(Integer.parseInt(parser.getAttributeValue(null, "heavy_lastscan")));
        result.setHeavyMass(Float.parseFloat(parser.getAttributeValue(null, "heavy_mass")));
        result.setLightArea(Float.parseFloat(parser.getAttributeValue(null, "light_area")));
        result.setLightFirstscan(Integer.parseInt(parser.getAttributeValue(null, "light_firstscan")));
        result.setLightLastscan(Integer.parseInt(parser.getAttributeValue(null, "light_lastscan")));
        result.setLightMass(Float.parseFloat(parser.getAttributeValue(null, "light_mass")));
        result.setRatio(parser.getAttributeValue(null, "ratio"));

        return result;
    }

    protected String getAnalysisType()
    {
        return analysisType;
    }

    public static class XPressResult extends RelativeQuantAnalysisResult
    {
        private String ratio;
        private String heavy2lightRatio;

        public String getRatio()
        {
            return ratio;
        }

        public void setRatio(String ratio)
        {
            this.ratio = ratio;
        }

        public String getHeavy2lightRatio()
        {
            return heavy2lightRatio;
        }

        public void setHeavy2lightRatio(String heavy2lightRatio)
        {
            this.heavy2lightRatio = heavy2lightRatio;
        }

        public String getAnalysisType()
        {
            return "xpress";
        }
    }
}
