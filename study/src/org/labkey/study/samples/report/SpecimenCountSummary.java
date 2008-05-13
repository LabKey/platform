package org.labkey.study.samples.report;

import org.labkey.study.samples.report.SpecimenReportCellData;

/**
 * Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Jan 29, 2008 5:27:13 PM
 */
public class SpecimenCountSummary implements SpecimenReportCellData
{
    private Double _sequenceNum;
    private Long _vialCount;
    private Double _totalVolume;

    public Double getSequenceNum()
    {
        return _sequenceNum;
    }

    public void setSequenceNum(Double sequenceNum)
    {
        _sequenceNum = sequenceNum;
    }

    public Long getVialCount()
    {
        return _vialCount;
    }

    public void setVialCount(Long vialCount)
    {
        _vialCount = vialCount;
    }

    public Double getTotalVolume()
    {
        return _totalVolume;
    }

    public void setTotalVolume(Double totalVolume)
    {
        _totalVolume = totalVolume;
    }
}
