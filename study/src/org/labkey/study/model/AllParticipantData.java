/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.apache.commons.collections.functors.InstantiateFactory;
import org.apache.commons.collections.map.MultiValueMap;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.PropertyUtil;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.study.Study;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.util.*;/*
 * User: brittp
 * Date: Jul 28, 2008
 * Time: 2:57:36 PM
 */

public class AllParticipantData
{
    private Set<Integer> _dataSetIds;
    private MultiValueMap _visitSequenceMap;
    private Map<ParticipantDataMapKey, RowSet> _valueMap;

    static final String allParticipantDataSql = "SELECT SD.DatasetId, PV.VisitRowId, SD.SequenceNum, SD._key, SD.SourceLsid, " +
            "exp.ObjectProperty.*, exp.PropertyDescriptor.PropertyURI, QC.RowId AS QCState\n" +
            "FROM study.studydata SD JOIN study.participantvisit PV ON SD.participantid=PV.participantid AND SD.sequencenum=PV.sequencenum\n" +
            "LEFT OUTER JOIN exp.Object ON SD.lsid = exp.Object.objecturi\n" +
            "LEFT OUTER JOIN exp.ObjectProperty ON exp.Object.ObjectId = exp.ObjectProperty.ObjectId\n" +
            "LEFT OUTER JOIN study.QCState QC ON SD.QCState = QC.RowId\n" +
            "JOIN exp.PropertyDescriptor ON exp.ObjectProperty.propertyid = exp.PropertyDescriptor.PropertyId\n" +
            "WHERE SD.container=? AND PV.container=? AND exp.Object.container=? AND SD.participantid=?";


    private static class VisitMultiMap extends MultiValueMap
    {
        VisitMultiMap()
        {
            super(new TreeMap(), new InstantiateFactory(TreeSet.class));
        }
    }

    private AllParticipantData(Set<Integer> dataSetIds, MultiValueMap visitSeqMap, Map<ParticipantDataMapKey, RowSet> valueMap)
    {
        _dataSetIds = dataSetIds;
        _visitSequenceMap = visitSeqMap;
        _valueMap = valueMap;
    }

    /*
     * Factory method to create AllParticipantData instances:
     */
    public static AllParticipantData get(Study study, String participantId, QCStateSet qcStateSet)
    {
        Table.TableResultSet rs = null;
        try
        {
            DbSchema schema = StudySchema.getInstance().getSchema();

            String sql = allParticipantDataSql;
            if (qcStateSet != null)
                sql += " AND " + qcStateSet.getStateInClause("SD.QCState");
            rs = Table.executeQuery(schema, sql,
                    new Object[] {study.getContainer().getId(), study.getContainer().getId(), study.getContainer().getId(), participantId});


            // What we have here is a map of participant/sequencenum, in other words an entry for each "visit" or "event"
            //
            // Each event gets a map of keys, this is usually a one entry map with the key "", but it for multi-entry assays
            // this map will have one entry for each key value
            //
            // this in turn, points to a map of propertyid/value, the actual patient data

            Map<ParticipantDataMapKey, RowSet> allData = new HashMap<ParticipantDataMapKey, RowSet>();
    //        Map<ParticipantDataMapKey, Object> allData = new HashMap<ParticipantDataMapKey, Object>();


            Set<Integer> datasetIds = new HashSet<Integer>();
            MultiValueMap visitSeqMap = new VisitMultiMap();

            int colDatasetId = rs.findColumn("DatasetId");
            int colKey = rs.findColumn("_key");
            int colVisitRowId = rs.findColumn("VisitRowId");
            int colSequenceNum = rs.findColumn("SequenceNum");
            int colQCStateIndex = rs.findColumn("QCState");
            int colSourceLsidIndex = rs.findColumn("SourceLsid");
            int colPropertyId = rs.findColumn("propertyId");

            final String defaultDateFormat = StudyManager.getInstance().getDefaultDateFormatString(study.getContainer());
            final String defaultNumberFormat = StudyManager.getInstance().getDefaultNumberFormatString(study.getContainer());

            while (rs.next())
            {
                ArrayListMap row = (ArrayListMap)rs.getRowMap();
                Integer datasetId = (Integer) row.get(colDatasetId);
                Double visitSequenceNum = (Double)row.get(colSequenceNum);
                Integer visitRowId = (Integer)row.get(colVisitRowId);
                Integer qcStateId = (Integer) row.get(colQCStateIndex);
                String sourceLsid = (String)row.get(colSourceLsidIndex);
                Integer propertyId = (Integer)row.get(colPropertyId);
                String key = (String)row.get(colKey);

                PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyId);
                Object val;
                String defaultFormat = null;

                switch (pd.getPropertyType())
                {
                    default:
                    case STRING:
                        val = row.get("StringValue");
                        break;
                    case DOUBLE:
                    case INTEGER:
                    case BOOLEAN:
                        val = row.get("FloatValue");
                        defaultFormat = defaultNumberFormat;
                        switch (pd.getPropertyType())
                        {
                            case INTEGER:
                                val = ((Double) val).intValue();
                                break;
                            case BOOLEAN:
                                val = ((Double) val) == 0 ? Boolean.FALSE : Boolean.TRUE;
                               break;
                            default:
                                break;
                        }
                        break;
                    case DATE_TIME:
                        val = row.get("DateTimeValue");
                        defaultFormat = defaultDateFormat;
                        break;
                }
                if (visitRowId != null && visitSequenceNum != null)
                    visitSeqMap.put(visitRowId, visitSequenceNum);
                datasetIds.add(datasetId);

                // OK navigate the compound map and add value
                ParticipantDataMapKey mapKey = new ParticipantDataMapKey(datasetId.intValue(), visitSequenceNum.doubleValue());
                RowSet keyMap = allData.get(mapKey);
                if (null == keyMap)
                {
                    keyMap = new RowSet(mapKey);
                    allData.put(mapKey, keyMap);
                }

                boolean extraKeyField = key != null;
                Row propMap = extraKeyField ? keyMap.get(key) : keyMap.get();
                if (null == propMap)
                {
                    QCState qcState = qcStateId != null ?
                            StudyManager.getInstance().getQCStateForRowId(study.getContainer(), qcStateId.intValue()) : null;

                    propMap = new Row(qcState, sourceLsid);
                    if (extraKeyField)
                        keyMap.set(propMap, key);
                    else
                        keyMap.set(propMap);
                }
                propMap.put(propertyId, PropertyUtil.formatValue(pd, val, defaultFormat));
            }

            return new AllParticipantData(datasetIds, visitSeqMap, allData);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            if (rs != null) //noinspection EmptyCatchBlock
                try { rs.close(); } catch (SQLException e) { }
        }
    }

    public Set<Integer> getDataSetIds()
    {
        return _dataSetIds;
    }

    public Map<ParticipantDataMapKey, RowSet> getValueMap()
    {
        return _valueMap;
    }

    public MultiValueMap getVisitSequenceMap()
    {
        return _visitSequenceMap;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(_dataSetIds.toString()).append("\n").append(_visitSequenceMap.keySet()).append("\n").append(_valueMap.toString());
        return sb.toString();
    }


    /**
     * ParticipantDatasetRow represents a single row of a single dataset.  It can be uniquely identified by
     * participant/visit/dataset in the case where the dataset does not have an additional key field, or by
     * participant/visit/dataset/extraKeyField if the dataset does have an additional key field.
     */
    public static class Row
    {
        private Map<Integer, Object> _dataMap = new HashMap<Integer, Object>();

        @Nullable
        private final QCState _qcState;

        @Nullable
        private final String _sourceLsid;

        public Row(QCState qcState, String sourceLsid)
        {
            _qcState = qcState;
            _sourceLsid = sourceLsid;
        }

        public void put(Integer propertyId, Object value)
        {
            _dataMap.put(propertyId, value);
        }

        public Object get(Integer propertyId)
        {
            return _dataMap.get(propertyId);
        }

        @Nullable
        public QCState getQCState()
        {
            return _qcState;
        }

        @Nullable
        public String getSourceLsid()
        {
            return _sourceLsid;
        }
    }

    /*
     * ParticipantDataMap provides an extra level of indirection so there's a place to handle multiple values per
     * participant/visit/dataset.  This occurrs when a dataset has an extra key field.  If the dataset has such a
     * field, the _extraKeyFieldToData map will contain multiple entries.  If the dataset does not contain an extra
     * key field, there will be at most one entry in the _extraKeyFieldToData map, with key NO_EXTRA_KEY_FIELD_KEY.
     */
    public static class RowSet
    {
        private Map<String, Row> _extraKeyFieldToData = null;
        private boolean _extraKeyField = false;
        private ParticipantDataMapKey _key;
        private static final String NO_EXTRA_KEY_FIELD_KEY = "";

        public RowSet(ParticipantDataMapKey key)
        {
            _key = key;
        }

        private void set(String extraKeyValue, Row datasetRow, boolean multiKey)
        {
            if (_extraKeyFieldToData != null)
            {
                assert multiKey == _extraKeyField : "Cannot change multi-key status once data has been added.";
            }
            else
            {
                _extraKeyField = multiKey;
                _extraKeyFieldToData = new TreeMap<String, Row>();
            }
            _extraKeyFieldToData.put(extraKeyValue, datasetRow);
        }

        public void set(Row datasetRow)
        {
            set(NO_EXTRA_KEY_FIELD_KEY, datasetRow, false);
        }

        public void set(Row datasetRow, String extraKeyValue)
        {
            set(extraKeyValue, datasetRow, true);
        }

        public ParticipantDataMapKey getKey()
        {
            return _key;
        }

        public boolean hasExtraKeyField()
        {
            return _extraKeyField;
        }

        public int getKeyFieldCount()
        {
            return _extraKeyFieldToData.size();
        }

        public Row get(String extraKeyValue)
        {
            if (_extraKeyFieldToData == null)
                return null;
            assert _extraKeyField : "Data stored without an extra key field cannot be accessed with an extra key field";
            return _extraKeyFieldToData.get(extraKeyValue);
        }

        public Row get()
        {
            if (_extraKeyFieldToData == null)
                return null;
            assert !_extraKeyField : "Data stored with an extra key field can only be accessed with an extra key field";
            return _extraKeyFieldToData.get(NO_EXTRA_KEY_FIELD_KEY);
        }

        public Collection<Row> getAll()
        {
            return _extraKeyFieldToData.values();
        }
    }
}