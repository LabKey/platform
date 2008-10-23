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

import java.util.Date;

public class RelativeQuantAnalysisSummary
{
    private int _quantId;
    private int _runId;
    private String _version;
    private String _labeledResidues;
    private String _massDiff;
    private float _massTol;
    private Date _analysisTime;
    private String _analysisType;

    /**
     * No arg constructor for BeanObjectFactory
     */
    public RelativeQuantAnalysisSummary()
    {
    }

    public RelativeQuantAnalysisSummary(String analysisType)
    {
        _analysisType = analysisType;
    }

    public String getAnalysisType()
    {
        return _analysisType;
    }

    public void setAnalysisType(String analysisType)
    {
        _analysisType = analysisType;
    }

    public int getQuantId()
    {
        return _quantId;
    }

    public void setQuantId(int quantId)
    {
        _quantId = quantId;
    }

    public int getRun()
    {
        return _runId;
    }

    public void setRun(int runId)
    {
        _runId = runId;
    }

    public Date getAnalysisTime()
    {
        return _analysisTime;
    }

    public void setAnalysisTime(Date analysisTime)
    {
        _analysisTime = analysisTime;
    }

    public String getVersion()
    {
	return _version;
    }

    public void setVersion(String version)
    {
	_version = version;
    }

    public String getMassDiff()
    {
	return _massDiff;
    }

    public void setMassDiff(String massDiff)
    {
	_massDiff = massDiff;
    }

    public float getMassTol()
    {
	return _massTol;
    }

    public void setMassTol(float massTol)
    {
	_massTol = massTol;
    }

    public String getLabeledResidues()
    {
	return _labeledResidues;
    }

    public void setLabeledResidues(String labeledResidues)
    {
	_labeledResidues = labeledResidues;
    }

    /**
     * Return a string representation of the analysis algorithm and version (if any)
     */
    public String getAnalysisAlgorithm()
    {
        String version = getVersion();
        return getAnalysisType() + (null == version ? "" : " " + version);
    }

}
