/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;

import java.sql.*;
import java.util.Date;
import java.util.Map;

/**
* User: jeckels
* Date: Apr 23, 2010
*/
class DatasetImportHelper implements OntologyManager.ImportHelper
{
    final String _containerId;
    final int _datasetId;
    final String _urnPrefix;
    final Connection _conn;
    PreparedStatement _stmt = null;
    final Long _lastModified;
    final String _visitDatePropertyURI;
    final String _keyPropertyURI;
    final Study _study;
    final DataSet _dataset;

    TableInfo tinfo = StudySchema.getInstance().getTableInfoStudyData();

    DatasetImportHelper(User user, Connection conn, Container c, DataSetDefinition dataset, long lastModified) throws SQLException, UnauthorizedException
    {
        _containerId = c.getId();
        _study = StudyManager.getInstance().getStudy(c);
        _datasetId = dataset.getDataSetId();
        _dataset = StudyManager.getInstance().getDataSetDefinition(_study, _datasetId);
        _urnPrefix = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":Study.Data-" + c.getRowId() + ":" + _datasetId + ".";
        _conn = conn;
        _lastModified = lastModified;
        if (null != conn)
        {
            SqlDialect dialect = StudyManager.getSchema().getSqlDialect();
            String strType = StudyManager.getSchema().getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR);
            _stmt = conn.prepareStatement(
                    "INSERT INTO " + tinfo + " (Container, DatasetId, ParticipantId, SequenceNum, LSID, _VisitDate, Created, Modified, SourceLsid, _key, QCState, ParticipantSequenceKey) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + dialect.concatenate("?", "'|'", "CAST(CAST(? AS NUMERIC(15, 4)) AS " + strType + ")") + ")");
            _stmt.setString(1, _containerId);
            _stmt.setInt(2, _datasetId);
        }

        String visitDatePropertyURI = null;
        String keyPropertyURI = null;
        for (ColumnInfo col : dataset.getTableInfo(user, false, false).getColumns())
        {
            if (col.getName().equalsIgnoreCase(dataset.getVisitDatePropertyName()))
                visitDatePropertyURI = col.getPropertyURI();
            if (col.getName().equalsIgnoreCase(dataset.getKeyPropertyName()))
                keyPropertyURI = col.getPropertyURI();
        }

        _visitDatePropertyURI = null == visitDatePropertyURI ? visitDateURI : visitDatePropertyURI;
        _keyPropertyURI = keyPropertyURI;
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
    static String createdURI = DataSetDefinition.getCreatedURI();
    static String modifiedURI = DataSetDefinition.getModifiedURI();
    static String sourceLsidURI = DataSetDefinition.getSourceLsidURI();
    static String qcStateURI = DataSetDefinition.getQCStateURI();


    public String getURI(Map map)
    {
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


    public String beforeImportObject(Map<String, Object> map) throws SQLException
    {
        if (null == _stmt)
            throw new IllegalStateException("No connection provided");

        String uri = getURI(map);
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
            visit = toDouble(map.get(visitSequenceNumURI));
        Object key = null == _keyPropertyURI ? null : map.get(_keyPropertyURI);

        Object created = map.get(createdURI);
        Long timeCreated = null == created ? _lastModified : toMs(created);
        Object modified = map.get(modifiedURI);
        Long timeModified = null == modified ? _lastModified : toMs(modified);
        Long visitDate = toMs(map.get(_visitDatePropertyURI));
        assert _dataset.isDemographicData() || _study.getTimepointType() == TimepointType.VISIT || null != visitDate;
        String sourceLsid = (String) map.get(sourceLsidURI);
        // Values coming in from the client API might get here as Doubles, though we really want an Integer
        Number qcState = (Number) map.get(qcStateURI);

        _stmt.setString(3, ptid);
        _stmt.setDouble(4, visit); // SequenceNum
        _stmt.setString(5, uri); // LSID
        _stmt.setTimestamp(6, null == visitDate ? null : new Timestamp(visitDate));
        _stmt.setTimestamp(7, null == timeCreated ? null : new Timestamp(timeCreated));
        _stmt.setTimestamp(8, null == timeModified ? null : new Timestamp(timeModified));
        _stmt.setString(9, sourceLsid);
        _stmt.setString(10, key == null ? "" : String.valueOf(key));
        if (qcState != null)
            _stmt.setInt(11, qcState.intValue());
        else
            _stmt.setNull(11, Types.INTEGER);

        // ParticipantSequenceKey (concatenation of "ptid|SequenceNum")
        _stmt.setString(12, ptid);
        _stmt.setDouble(13, visit);

        _stmt.execute();
        return uri;
    }


    private Long toMs(Object date)
    {
        if (null == date)
            return null;
        if (date instanceof String)
        {
            try{ return DateUtil.parseDateTime((String)date);}
            catch (ConversionException x) { return null; }
        }
        if (date instanceof Date)
            return ((Date)date).getTime();
        return null;
    }


    public void afterBatchInsert(int currentRow) throws SQLException
    {
    }

    public void updateStatistics(int currentRow) throws SQLException
    {
        tinfo.getSqlDialect().updateStatistics(tinfo);
    }


    public void done()
    {
        try
        {
            if (null != _stmt)
                _stmt.close();
            _stmt = null;
        }
        catch (SQLException x)
        {
            Logger.getLogger(DatasetImportHelper.class).error("unexpected error", x);
        }
    }
}
