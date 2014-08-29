/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.study.model;

import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.UnauthorizedException;

import java.sql.*;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Map;

/**
* User: jeckels
* Date: Apr 23, 2010
*/
class DatasetImportHelper implements OntologyManager.UpdateableTableImportHelper
{
    final String _containerId;
    final int _datasetId;
    final String _urnPrefix;
    final Long _lastModified;
    final String _visitDatePropertyURI;
    final String _keyPropertyURI;
    final Study _study;
    final DataSet _dataset;

    final TimepointType _timetype;
    final DecimalFormat _sequenceFormat; 

    DatasetImportHelper(User user, DataSetDefinition dataset, long lastModified) throws SQLException, UnauthorizedException
    {
        _containerId = dataset.getContainer().getId();
        _study = dataset.getStudy();
        _datasetId = dataset.getDatasetId();
        _dataset = StudyManager.getInstance().getDatasetDefinition(_study, _datasetId);
        _urnPrefix = dataset.getURNPrefix();
        _lastModified = lastModified;

        _timetype = _study.getTimepointType();
        _sequenceFormat = new DecimalFormat("0.0000");

        String visitDatePropertyURI = null;
        String keyPropertyURI = null;
        TableInfo datasetTable = dataset.getTableInfo(user,false);
        for (ColumnInfo col : datasetTable.getColumns())
        {
            if (col.getName().equalsIgnoreCase(dataset.getVisitDateColumnName()))
                visitDatePropertyURI = col.getPropertyURI();
            if (col.getName().equalsIgnoreCase(dataset.getKeyPropertyName()))
                keyPropertyURI = col.getPropertyURI();
        }

        _visitDatePropertyURI = null == visitDatePropertyURI ? visitDateURI : visitDatePropertyURI;
        _keyPropertyURI = keyPropertyURI;
    }


    public void addParameters(Map<String,Object> row, Parameter.ParameterMap parameterMap)
    {
//        String lsid = getURI(row);
//        String participantId = getParticipantId(row);
//        double sequenceNum = getSequenceNum(row);
//        Object key = getKey(row);
//        String participantSequenceNum = participantId + "|" + _sequenceFormat.format(sequenceNum);
//        Date visitDate = getVisitDate(row);
//        Integer qcState = getQCState(row);
//        String sourceLsid = getSourceLsid(row);
//
//        parameterMap.put("participantsequencenum", participantSequenceNum);
//        parameterMap.put("participantid", participantId);
//        parameterMap.put("sequencenum", sequenceNum);
//        parameterMap.put("_key", key==null ? "" : String.valueOf(key));
//        parameterMap.put("lsid", lsid);
//        parameterMap.put("qcstate", qcState);
//        parameterMap.put("sourcelsid", sourceLsid);
//        if (_timetype != TimepointType.VISIT)
//            parameterMap.put("date", visitDate);
    }

    
    static double toDouble(Object i)
    {
        if (i == null)
            return 0;
        else if (i instanceof Number)
            return ((Number) i).doubleValue();
        throw new IllegalArgumentException("Unexpected type " + i.getClass() + ": " + i);
    }


    static String participantURI = DataSetDefinition.getParticipantIdURI();
    static String visitSequenceNumURI = DataSetDefinition.getSequenceNumURI();
    static String visitDateURI = DataSetDefinition.getVisitDateURI();
    static String sourceLsidURI = DataSetDefinition.getSourceLsidURI();
    static String qcStateURI = DataSetDefinition.getQCStateURI();


    public String getURI(Map map)
    {
        // Note - this should generate the same value as how we do it in SQL in StudyManager.updateDataSetDefinition
        String ptid = String.valueOf(map.get(participantURI));
        double visit;
        if (_study.getTimepointType() != TimepointType.VISIT)
        {
            Date date = (Date)(ConvertUtils.lookup(Date.class).convert(Date.class, map.get(visitDateURI)));
            if (null != date)
                visit = StudyManager.sequenceNumFromDate(date);
            else
                visit = VisitImpl.DEMOGRAPHICS_VISIT;
        }
        else
            visit = toDouble(map.get(DataSetDefinition.getSequenceNumURI()));
        StringBuilder sb = new StringBuilder(_urnPrefix);
        sb.append(visit).append('.').append(ptid);
        if (null != _keyPropertyURI)
        {
            Object key = map.get(_keyPropertyURI);
            if (null != key)
                sb.append('.').append(key);
        }
        return sb.toString();
    }


    public double getSequenceNum(Map map)
    {
        if (_study.getTimepointType() != TimepointType.VISIT)
        {
            Date date = (Date)(ConvertUtils.lookup(Date.class).convert(Date.class, map.get(visitDateURI)));
            if (null != date)
                return StudyManager.sequenceNumFromDate(date);
            else
                return VisitImpl.DEMOGRAPHICS_VISIT;
        }
        else
            return toDouble(map.get(visitSequenceNumURI));
    }


    public String getParticipantId(Map map)
    {
        return String.valueOf(map.get(participantURI));
    }


    public Object getKey(Map map)
    {
        return null == _keyPropertyURI ? null : map.get(_keyPropertyURI);    
    }


    public Date getVisitDate(Map map)
    {
        return (Date) map.get(_visitDatePropertyURI);    
    }

    public Integer getQCState(Map map)
    {
        Number qcState = (Number)map.get(qcStateURI);
        return null == qcState ? null : qcState instanceof Integer  ? (Integer)qcState : qcState.intValue();
    }

    public String getSourceLsid(Map map)
    {
        return (String)map.get(sourceLsidURI);
    }

    public String beforeImportObject(Map<String, Object> row) throws SQLException
    {
        String lsid = getURI(row);
        String participantId = getParticipantId(row);
        double sequenceNum = getSequenceNum(row);
        String participantSequenceNum = participantId + "|" + _sequenceFormat.format(sequenceNum);
        Date visitDate = getVisitDate(row);
        Integer qcState = getQCState(row);
        String sourceLsid = getSourceLsid(row);

        row.put("participantsequencenum", participantSequenceNum);
        row.put("participantid", participantId);
        row.put("sequencenum", sequenceNum);
        row.put("lsid", lsid);
        row.put("qcstate", qcState);
        row.put("sourcelsid", sourceLsid);
        if (_timetype != TimepointType.VISIT)
            row.put("date", visitDate);
        return lsid;
    }

    @Override
    public void bindAdditionalParameters(Map<String, Object> row, Parameter.ParameterMap target) throws ValidationException
    {
        Object key = getKey(row);
        target.put("_key", key==null ? "" : String.valueOf(key));
    }

    @Override
    public void afterImportObject(Map<String, Object> map) throws SQLException
    {
    }

    public void afterBatchInsert(int currentRow) throws SQLException
    {
    }

    public void updateStatistics(int currentRow) throws SQLException
    {
    }

    public void done()
    {
    }
}
