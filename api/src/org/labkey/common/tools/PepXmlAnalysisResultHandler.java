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
import java.util.Map;
import java.util.HashMap;

/**
 * User: arauch
 * Date: Feb 16, 2006
 * Time: 1:05:38 PM
 */
public abstract class PepXmlAnalysisResultHandler
{
    private static Map<String, PepXmlAnalysisResultHandler> _handlers;

    static
    {
        _handlers = new HashMap<String, PepXmlAnalysisResultHandler>(10);

        register(new PeptideProphetHandler());
        register(new XPressHandler());
        register(new Q3Handler());
    }

    private static void register(PepXmlAnalysisResultHandler handler)
    {
        _handlers.put(handler.getAnalysisType(), handler);
    }


    protected abstract String getAnalysisType();
    protected abstract PepXmlAnalysisResult getResult(SimpleXMLStreamReader parser) throws XMLStreamException;

    protected static void setAnalysisResult(SimpleXMLStreamReader parser, PepXmlLoader.PepXmlPeptide peptide)throws XMLStreamException
    {
        String analysisType = parser.getAttributeValue(null, "analysis");
        PepXmlAnalysisResultHandler handler = _handlers.get(analysisType);

        if (null != handler)
        {
            PepXmlAnalysisResult result = handler.getResult(parser);
            peptide.addAnalysisResult(result.getAnalysisType(), result);
        }

        parser.skipToEnd("analysis_result");
        parser.next();
    }

    public static abstract class PepXmlAnalysisResult
    {
        public abstract String getAnalysisType();
    }

    /**
     * Special handling for "inf" values
     * @param floatString
     * @return
     */
    protected Float parseFloatHandleInf(String floatString)
    {
        try
        {
            return Float.parseFloat(floatString);
        }
        catch (RuntimeException e)
        {
            if ("inf".equals(floatString) || "1.#J".equals(floatString) || "infinity".equals(floatString)
                    || "INF".equals(floatString) || "INFINITY".equals(floatString))
                return Float.POSITIVE_INFINITY;
            else if ("nan".equals(floatString) || "NAN".equals(floatString))
                return Float.NaN;
            else if ("-inf".equals(floatString) || "-1.#J".equals(floatString) || "-infinity".equals(floatString)
                    || "-INF".equals(floatString) || "-INFINITY".equals(floatString))
                return Float.NEGATIVE_INFINITY;
            else
                throw e;
        }
    }
}
