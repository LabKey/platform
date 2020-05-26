/*
 * Copyright (c) 2014-2018 LabKey Corporation
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
package org.labkey.study.controllers;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewForm;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * User: cnathe
 * Date: 1/16/14
 */
public class VisitForm extends ViewForm
{
    private int[] _datasetIds;
    private String[] _datasetStatus;
    private BigDecimal _sequenceNumMin;
    private BigDecimal _sequenceNumMax;
    private BigDecimal _protocolDay;
    private Character _typeCode;
    private boolean _showByDefault;
    private Integer _cohortId;
    private String _label;
    private String _description;
    private VisitImpl _visit;
    private int _visitDateDatasetId;
    private String _sequenceNumHandling;
    private boolean _reshow;

    public VisitForm()
    {
    }


    public void validate(Errors errors, Study study)
    {
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
        {
            errors.reject(null, "Unsupported operation for continuous date study");
            return;
        }

        HttpServletRequest request = getRequest();
        String oldValues = request.getParameter(DataRegion.OLD_VALUES_NAME);

        if (null != StringUtils.trimToNull(oldValues))
        {
            try
            {
                _visit = PageFlowUtil.decodeObject(VisitImpl.class, oldValues);
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
        }

        //check for null min/max sequence numbers
        if (null == getSequenceNumMax() && null == getSequenceNumMin())
            errors.reject(null, "You must specify at least a minimum or a maximum value for the visit range.");

        //if min is null but max is not, set min to max and vice-versa
        if (null == getSequenceNumMin() && null != getSequenceNumMax())
            setSequenceNumMin(getSequenceNumMax());
        if (null == getSequenceNumMax() && null != getSequenceNumMin())
            setSequenceNumMax(getSequenceNumMin());

        if (!(Visit.MIN_SEQUENCE_NUM.compareTo(getSequenceNumMin()) < 0 && getSequenceNumMin().compareTo(Visit.MAX_SEQUENCE_NUM) < 0))
            errors.rejectValue("sequenceNumMin", null, "Out of range");
        if (!(Visit.MIN_SEQUENCE_NUM.compareTo(getSequenceNumMax()) < 0 && getSequenceNumMax().compareTo(Visit.MAX_SEQUENCE_NUM) < 0))
            errors.rejectValue("sequenceNumMax", null, "Out of range");

        // if target sequence num is null, set to min
        if (null == getProtocolDay() && TimepointType.DATE == study.getTimepointType())
            setProtocolDay(VisitImpl.calcDefaultDateBasedProtocolDay(getSequenceNumMin(), getSequenceNumMax()));

        VisitImpl visit = getBean();
        if (visit.getSequenceNumMin().compareTo(visit.getSequenceNumMax()) > 0)
        {
            errors.reject(null, "The minimum value cannot be greater than the maximum value for the visit range.");
/*
                double min = visit.getSequenceNumMax();
                double max = visit.getSequenceNumMin();
                visit.setSequenceNumMax(max);
                visit.setSequenceNumMin(min);
*/
        }
        setBean(visit);
    }

    private Container getVisitContainer()
    {
        Study study = StudyManager.getInstance().getStudy(getContainer());
        Study visitStudy = StudyManager.getInstance().getStudyForVisits(study);
        return visitStudy.getContainer();
    }


    public VisitImpl getBean()
    {
        if (null == _visit)
            _visit = new VisitImpl();

        _visit.setContainer(getVisitContainer());

        if (getTypeCode() != null)
            _visit.setTypeCode(getTypeCode());

        _visit.setLabel(getLabel());
        _visit.setDescription(getDescription());

        if (null != getSequenceNumMax())
            _visit.setSequenceNumMax(getSequenceNumMax());
        if (null != getSequenceNumMin())
            _visit.setSequenceNumMin(getSequenceNumMin());
        if (null != getProtocolDay())
            _visit.setProtocolDay(getProtocolDay());

        _visit.setCohortId(getCohortId());
        _visit.setVisitDateDatasetId(getVisitDateDatasetId());

        _visit.setSequenceNumHandling(getSequenceNumHandling());
        _visit.setShowByDefault(isShowByDefault());

        return _visit;
    }

    public void setBean(VisitImpl bean)
    {
        if (null != bean.getSequenceNumMax())
            setSequenceNumMax(bean.getSequenceNumMax());
        if (null != bean.getSequenceNumMin())
            setSequenceNumMin(bean.getSequenceNumMin());
        if (null != bean.getProtocolDay())
            setProtocolDay(bean.getProtocolDay());
        if (null != bean.getType())
            setTypeCode(bean.getTypeCode());
        setLabel(bean.getLabel());
        setCohortId(bean.getCohortId());
        setSequenceNumHandling(bean.getSequenceNumHandling());
    }

    public String[] getDatasetStatus()
    {
        return _datasetStatus;
    }

    public void setDatasetStatus(String[] datasetStatus)
    {
        _datasetStatus = datasetStatus;
    }

    public int[] getDatasetIds()
    {
        return _datasetIds;
    }

    public void setDatasetIds(int[] datasetIds)
    {
        _datasetIds = datasetIds;
    }

    public BigDecimal getSequenceNumMin()
    {
        return _sequenceNumMin;
    }

    public void setSequenceNumMin(BigDecimal sequenceNumMin)
    {
        _sequenceNumMin = sequenceNumMin;
    }

    public BigDecimal getSequenceNumMax()
    {
        return _sequenceNumMax;
    }

    public void setSequenceNumMax(BigDecimal sequenceNumMax)
    {
        _sequenceNumMax = sequenceNumMax;
    }

    public BigDecimal getProtocolDay()
    {
        return _protocolDay;
    }

    public void setProtocolDay(BigDecimal protocolDay)
    {
        _protocolDay = protocolDay;
    }

    public Character getTypeCode()
    {
        return _typeCode;
    }

    public void setTypeCode(Character typeCode)
    {
        _typeCode = typeCode;
    }

    public boolean isShowByDefault()
    {
        return _showByDefault;
    }

    public void setShowByDefault(boolean showByDefault)
    {
        _showByDefault = showByDefault;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId = cohortId;
    }

    public int getVisitDateDatasetId()
    {
        return _visitDateDatasetId;
    }

    public void setVisitDateDatasetId(int visitDateDatasetId)
    {
        _visitDateDatasetId = visitDateDatasetId;
    }

    public String getSequenceNumHandling()
    {
        return _sequenceNumHandling;
    }

    public void setSequenceNumHandling(String sequenceNumHandling)
    {
        _sequenceNumHandling = sequenceNumHandling;
    }

    public boolean isReshow()
    {
        return _reshow;
    }

    public void setReshow(boolean reshow)
    {
        _reshow = reshow;
    }
}
