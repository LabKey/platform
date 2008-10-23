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

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * User: jeckels
 * Date: Mar 13, 2006
 */
public class SensitivitySummary
{
    protected float[] _minProb;
    protected float[] _sensitivity;
    protected float[] _error;
    private int _runId;

    public byte[] getMinProbSeries()
    {
        return PeptideProphetSummary.toByteArray(_minProb);
    }

    public void setMinProbSeries(byte[] minProb)
    {
        setMinProb(PeptideProphetSummary.toFloatArray(minProb));
    }

    private void setMinProb(float[] floats)
    {
        _minProb = floats;
        sortValues();
    }

    public byte[] getSensitivitySeries()
    {
        return PeptideProphetSummary.toByteArray(_sensitivity);
    }

    public void setSensitivitySeries(byte[] sensitivity)
    {
        setSensitivities(PeptideProphetSummary.toFloatArray(sensitivity));
    }

    private void setSensitivities(float[] floats)
    {
        _sensitivity = floats;
        sortValues();
    }

    public byte[] getErrorSeries()
    {
        return PeptideProphetSummary.toByteArray(_error);
    }

    public void setErrorSeries(byte[] error)
    {
        setErrors(PeptideProphetSummary.toFloatArray(error));
    }

    private void setErrors(float[] floats)
    {
        _error = floats;
        sortValues();
    }

    private void sortValues()
    {
        if (_sensitivity != null &&
            _error != null &&
            _minProb != null &&
            _sensitivity.length == _minProb.length &&
            _minProb.length == _error.length)
        {
            List<ProbabilityInfo> infos = new ArrayList<ProbabilityInfo>(_sensitivity.length);
            for (int i = 0; i < _sensitivity.length; i++)
            {
                infos.add(new ProbabilityInfo(_minProb[i], _sensitivity[i], _error[i]));
            }
            Collections.sort(infos);
            for (int i = 0; i < infos.size(); i++)
            {
                ProbabilityInfo info = infos.get(i);
                _minProb[i] = info._probability;
                _sensitivity[i] = info._sensitivity;
                _error[i] = info._error;
            }
        }
    }

    public int getRun()
    {
        return _runId;
    }

    public void setRun(int runId)
    {
        _runId = runId;
    }

    public float[] getMinProb()
    {
        return _minProb;
    }

    public float[] getSensitivity()
    {
        return _sensitivity;
    }

    public float[] getError()
    {
        return _error;
    }

    protected class ProbabilityInfo implements Comparable
    {
        private final float _probability;
        private final float _sensitivity;
        private final float _error;

        public ProbabilityInfo(float probability, float sensitivity, float error)
        {
            _probability = probability;
            _sensitivity = sensitivity;
            _error = error;
        }

        public int compareTo(Object o)
        {
            ProbabilityInfo p = (ProbabilityInfo)o;
            return Float.compare(_probability, p._probability);
        }
    }

    public Float calculateErrorRate(float probability)
    {
        if (_minProb != null && _minProb.length > 0)
        {
            if (probability < 0)
            {
                return 1.0f;
            }
            float nextLowestProbability = 0;
            float nextHighestProbability = 1;
            float nextLowestProbabilityErrorRate = 1;
            float nextHighestProbabilityErrorRate = 0;
            for (int i = 0; i < _minProb.length; i++)
            {
                if (_minProb[i] == probability)
                {
                    return _error[i];
                }
                if (_minProb[i] <= probability && _minProb[i] > nextLowestProbability)
                {
                    nextLowestProbability = _minProb[i];
                    nextLowestProbabilityErrorRate = _error[i];
                }
                if (_minProb[i] >= probability && _minProb[i] < nextHighestProbability)
                {
                    nextHighestProbability = _minProb[i];
                    nextHighestProbabilityErrorRate = _error[i];
                }
            }
            assert nextLowestProbability < nextHighestProbability;
            assert nextLowestProbabilityErrorRate >= nextHighestProbabilityErrorRate;

            return nextHighestProbabilityErrorRate +
                (nextLowestProbabilityErrorRate - nextHighestProbabilityErrorRate) *
                    ((nextHighestProbability - probability) / (nextHighestProbability - nextLowestProbability));
        }

        return null;
    }
}
