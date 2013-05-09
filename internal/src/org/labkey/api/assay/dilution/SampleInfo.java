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

package org.labkey.api.assay.dilution;

import java.io.Serializable;

/**
 * User: brittp
 * Date: Aug 18, 2006
 * Time: 3:55:25 PM
 */
public class SampleInfo implements Serializable
{
    private static final long serialVersionUID = -8338877129594854955L;

    public enum Method
    {
        Concentration
        {
            public String getAbbreviation()
            {
                return "Conc.";
            }
        },
        Dilution
        {
            public String getAbbreviation()
            {
                return "Dilution";
            }
        };

        public String getFullName()
        {
            return name();
        }

        public abstract String getAbbreviation();
    }

    //ISSUE: LSIDS??
    private String _sampleId;
    private SafeTextConverter.DoubleConverter _initialDilution = new SafeTextConverter.DoubleConverter(new Double(20));
    private SafeTextConverter.DoubleConverter _factor = new SafeTextConverter.DoubleConverter(new Double(3));
    private String _dilutionSummaryLsid;
    private String _sampleDescription;
    private Method _method;

    public SampleInfo(String sampleId)
    {
        this._sampleId = sampleId;
    }

    public String getSampleId()
    {
        return _sampleId;
    }

    public void setSampleId(String sampleId)
    {
        this._sampleId = sampleId;
    }

    public Double getInitialDilution()
    {
        return _initialDilution.getValue();
    }

    public void setInitialDilution(Double initialDilution)
    {
        _initialDilution.setValue(initialDilution);
    }

    public String getInitialDilutionText()
    {
        return _initialDilution.getText();
    }

    public void setInitialDilutionText(String initialDilutionText)
    {
        _initialDilution.setText(initialDilutionText);
    }

    public Double getFactor()
    {
        return _factor.getValue();
    }

    public void setFactor(Double factor)
    {
        _factor.setValue(factor);
    }

    public String getFactorText()
    {
        return _factor.getText();
    }

    public void setFactorText(String factorText)
    {
        _factor.setText(factorText);
    }

    public String getDilutionSummaryLsid()
    {
        return _dilutionSummaryLsid;
    }

    public void setDilutionSummaryLsid(String dilutionSummaryLsid)
    {
        _dilutionSummaryLsid = dilutionSummaryLsid;
    }

    public String getMethodName()
    {
        return _method.name();
    }

    public void setMethodName(String methodName)
    {
        this._method = Method.valueOf(methodName);
    }

    public Method getMethod()
    {
        return _method;
    }

    public String getSampleDescription()
    {
        return _sampleDescription;
    }

    public void setSampleDescription(String sampleDescription)
    {
        _sampleDescription = sampleDescription;
    }
}
