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
public class PeptideProphetHandler extends PepXmlAnalysisResultHandler
{
    public final static String analysisType = "peptideprophet";

    protected PeptideProphetResult getResult(SimpleXMLStreamReader parser) throws XMLStreamException
    {
        PeptideProphetResult result = new PeptideProphetResult();
        parser.skipToStart("peptideprophet_result");
        String probability = parser.getAttributeValue(null, "probability");
        result.setProbability(Float.parseFloat(probability));

        String allNttProb = parser.getAttributeValue(null, "all_ntt_prob");
        if (allNttProb != null)
            result.setAllNttProb(allNttProb);

        while (parser.hasNext())
        {
            if (parser.isEndElement() && "peptideprophet_result".equals(parser.getLocalName()))
            {
                return result;
            }
            parser.next();

            if (parser.isStartElement() && "search_score_summary".equals(parser.getLocalName()))
            {
                handleSummary(parser, result);
            }
        }
        return result;
    }

    private void handleSummary(SimpleXMLStreamReader parser, PeptideProphetResult result) throws XMLStreamException
    {
        while (parser.hasNext())
        {
            if (parser.isEndElement() && "search_score_summary".equals(parser.getLocalName()))
            {
                return;
            }
            parser.next();
            if (parser.isStartElement() && parser.getLocalName().equals("parameter"))
            {
                String name = parser.getAttributeValue(null, "name");
                String value = parser.getAttributeValue(null, "value");

                if ("fval".equals(name))
                    result.setProphetFval(Float.parseFloat(value));
                else if ("massd".equals(name))
                    result.setProphetDeltaMass(Float.parseFloat(value));
                else if ("ntt".equals(name))
                    result.setProphetNumTrypticTerm(Integer.parseInt(value));
                else if ("nmc".equals(name))
                    result.setProphetNumMissedCleav(Integer.parseInt(value));
            }
        }
    }


    protected String getAnalysisType()
    {
        return analysisType;
    }


    public static class PeptideProphetResult extends PepXmlAnalysisResult
    {
        private float _fval;
        private float _massd;
        private int _ntt;
        private int _nmc;
        private long _peptideId;
        private String _allNttProb;

        private boolean _summaryLoaded;

        private float _probability;

        public float getProbability()
        {
            return _probability;
        }

        public void setProbability(float probability)
        {
            _probability = probability;
        }

        public String getAnalysisType()
        {
            return "peptideprophet";
        }

        public float getProphetFval()
        {
            return _fval;
        }

        public void setProphetFval(float _fval)
        {
            this._summaryLoaded = true;
            this._fval = _fval;
        }

        public float getProphetDeltaMass()
        {
            return _massd;
        }

        public void setProphetDeltaMass(float _massd)
        {
            this._massd = _massd;
        }

        public int getProphetNumTrypticTerm()
        {
            return _ntt;
        }

        public void setProphetNumTrypticTerm(int _ntt)
        {
            this._ntt = _ntt;
        }

        public int getProphetNumMissedCleav()
        {
            return _nmc;
        }

        public void setProphetNumMissedCleav(int _nmc)
        {
            this._nmc = _nmc;
        }

        public long getPeptideId()
        {
            return _peptideId;
        }

        public void setPeptideId(long peptideId)
        {
            this._peptideId = peptideId;
        }

        public boolean isSummaryLoaded()
        {
            return _summaryLoaded;
        }


        public String getAllNttProb()
        {
            return _allNttProb;
        }

        public void setAllNttProb(String _allNttProb)
        {
            this._allNttProb = _allNttProb;
        }
    }
}
