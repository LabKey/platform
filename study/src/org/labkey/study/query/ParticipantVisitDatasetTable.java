/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.study.query;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitDataset;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.visitmanager.VisitManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ParticipantVisitDatasetTable extends VirtualTable
{
    StudyImpl _study;
    StudyQuerySchema _schema;
    DatasetDefinition _dataset;
    ColumnInfo _colParticipantId;
    Map<Double,ColumnInfo> _seqColumnMap = new HashMap<>();

    public ParticipantVisitDatasetTable(StudyQuerySchema schema, DatasetDefinition dsd, ColumnInfo colParticipantId)
    {
        super(StudySchema.getInstance().getSchema(), null);
        StudyManager studyManager = StudyManager.getInstance();
        _study = studyManager.getStudy(schema.getContainer());
        assert _study.getTimepointType() != TimepointType.CONTINUOUS;
        _colParticipantId = colParticipantId;
        _dataset = dsd;
        _schema = schema;

        // all visits
        VisitManager visitManager = studyManager.getVisitManager(_study);
        TreeMap<Double, VisitImpl> visitSequenceMap = visitManager.getVisitSequenceMap();
        TreeMap<Integer, VisitImpl> visitRowIdMap = new TreeMap<>();
        for (VisitImpl v : visitSequenceMap.values())
            visitRowIdMap.put(v.getRowId(), v);

        // visits for this dataset
        // NOTE vdsList (and therefore visitList) is in display order
        List<VisitDataset> vdsList = _dataset.getVisitDatasets();
        Set<Integer> visitIds = new HashSet<>();
        List<VisitImpl> visitList = new ArrayList<>(vdsList.size());
        for (VisitDataset vds : vdsList)
        {
            VisitImpl visit = visitRowIdMap.get(vds.getVisitRowId());
            if (null != visit && null != StringUtils.trimToNull(visit.getLabel()))
            {
                visitList.add(visit);
                visitIds.add(vds.getVisitRowId());
            }
        }

        Set<Double> sequenceSet = new TreeSet<>();
        for (VisitDataset vds : vdsList)
        {
            VisitImpl visit = visitRowIdMap.get(vds.getVisitRowId());
            if (null == visit)
                continue;
            sequenceSet.add(visit.getSequenceNumMin());
        }
        //Now find all the sequenceNums where data actually exists.
        //Make sure their visits show up...
        List<Double> currentSequenceNumbers = _schema.getSequenceNumsForDataset(_dataset);
        if (null != currentSequenceNumbers)
        {
            for (Double d : currentSequenceNumbers)
            {
                sequenceSet.add(d);
                VisitImpl visit = visitManager.findVisitBySequence(d.doubleValue());
                if (null != visit && visitIds.add(visit.getRowId()))
                    visitList.add(visit);
            }
            //Resort the visit list
            visitList.sort((v1, v2) -> v1.getDisplayOrder() != v2.getDisplayOrder() ? v1.getDisplayOrder() - v2.getDisplayOrder() : (int) (v1.getSequenceNumMin() - v2.getSequenceNumMin()));
        }

        // duplicate label check a) two visits with same label b) two sequences with same visit
        MultiValuedMap<String, Double> labelMap = new ArrayListValuedHashMap<>();

        for (double seq : sequenceSet)
        {
            VisitImpl visit = visitManager.findVisitBySequence(seq);
            if (null == visit)
                continue;
            labelMap.put(_schema.decideTableName(visit), seq);
        }

        // nested loop preserves visit display order
        // UNDONE: except that addColumn() does not preserve order
        for (VisitImpl visit : visitList)
        {
            boolean uniqueLabel = labelMap.get(visit.getLabel()).size() == 1;
            boolean hasSequenceRange = visit.getSequenceNumMin() != visit.getSequenceNumMax();

            // add columns for each sequence, show if there is a sequence range
            for (double seq : sequenceSet)
            {
                if (!_inSequence(visit, seq))
                    continue;
                String name = "seq" + VisitImpl.formatSequenceNum(seq);
                String label = visit.getLabel();
                if (!uniqueLabel || hasSequenceRange)
                    label += " (" + VisitImpl.formatSequenceNum(seq) + ")";
                ColumnInfo colSeq = createVisitDatasetColumn(name, seq, visit);
                colSeq.setLabel(label);
//                colSeq.setHidden(!hasSequenceRange);
                addColumn(colSeq);
                this._seqColumnMap.put(seq, colSeq);
            }

/*
            if (uniqueLabel)
            {
                String name = _schema.decideTableName(visit);
                if (getColumn(name) != null)        // unlikely (label that looks like seq###.#)
                    continue;
                ColumnInfo colLabel = createVisitDatasetColumn(name, Visit.formatSequenceNum(visit.getSequenceNumMin()));
                colLabel.setHidden(hasSequenceRange);
                addColumn(colLabel);
            }
*/
        }
    }


    private static class PVDatasetLookupColumn extends LookupColumn
    {
        private final Study _study;
        private final VisitImpl _visit;
        private final double _sequenceNum;
        
        PVDatasetLookupColumn(Study study, VisitImpl visit, double sequenceNum, ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn)
        {
            super(foreignKey, lookupKey, lookupColumn);
            copyAttributesFrom(lookupColumn);
            copyURLFrom(lookupColumn, foreignKey.getFieldKey(), null);
            setLabel(foreignKey.getLabel() + " " + lookupColumn.getLabel());
            _study = study;
            _visit = visit;
            _sequenceNum = sequenceNum;
        }

        @Override
        public SQLFragment getJoinCondition(String baseAliasName)
        {
            SQLFragment sqlf = super.getJoinCondition(baseAliasName);
            if (_study.getTimepointType() == TimepointType.DATE)
            {
                // We need to join differently if this is a specimen table, since the subject column name is 'PTID'
                // rather than 'ParticipantId'.
                boolean specimenTable = getParentTable() instanceof AbstractSpecimenTable || getParentTable() instanceof SpecimenSummaryTable;
                sqlf.append(" AND ");
                sqlf.append(getTableAlias(baseAliasName)).append(".SequenceNum IN (select pv.sequencenum " +
                    "from " +
                    "study.participantvisit pv " +
                    "where " +
                    "pv.participantid = (");
                sqlf.append(baseAliasName + "." + (specimenTable ? "PTID" : "ParticipantId"));
                sqlf.append(") and pv.visitrowid = ?)");
                sqlf.add(_visit.getRowId());
            }
            else if (_study.getTimepointType() == TimepointType.VISIT)
            {
                sqlf.append(" AND ");
                sqlf.append(getTableAlias(baseAliasName)).append(".SequenceNum=CAST(? AS NUMERIC(15,4))");
                sqlf.add(_sequenceNum);
            }
            else
            {
                // XXX: continuous date based studies?
            }
            return sqlf;
        }
    }


    private static boolean _inSequence(VisitImpl v, double seq)
    {
        assert v.getSequenceNumMin() <= v.getSequenceNumMax();
        return seq >= v.getSequenceNumMax() && seq <= v.getSequenceNumMax();
    }

    
    protected ColumnInfo createVisitDatasetColumn(String name, final double sequenceNum, @NotNull final VisitImpl visit)
    {
        ColumnInfo ret;
        if (_colParticipantId == null)
        {
            ret = new ColumnInfo(name, this);
            ret.setSqlTypeName("VARCHAR");
        }
        else
        {
            ret = new AliasedColumn(name, _colParticipantId);
        }
        ret.setFk(new AbstractForeignKey() {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayFieldName)
            {
                TableInfo table = getLookupTableInfo();
                if (table == null)
                    return null;
                if (displayFieldName == null)
                {
                    //displayField = table.getTitleColumn();
                    // Data Set tables don't have an interesting title column.
                    return null;
                }
                ColumnInfo displayField = table.getColumn(displayFieldName);
                if (null == displayField)
                    return null;

                return new PVDatasetLookupColumn(
                        _study, visit, sequenceNum,
                        parent, table.getColumn(_study.getSubjectColumnName()), displayField);
            }

            public TableInfo getLookupTableInfo()
            {
                try
                {
                    DatasetTableImpl dsTable = _schema.createDatasetTableInternal(_dataset);
                    dsTable.hideParticipantLookups();
                    return dsTable;
                }
                catch (UnauthorizedException e)
                {
                    return null;
                }
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        });
        return ret;
    }


    protected ColumnInfo resolveColumn(String name)
    {
        double seq = -1;
        if (name.startsWith("seq"))
        {
            try
            {
                seq = VisitImpl.parseSequenceNum(name.substring("seq".length()));
            }
            catch (NumberFormatException x)
            {
                //
            }
        }

        VisitImpl visitMatch = null;

        // UNDONE: if/when visits have names use Visit.getName() before Visit.getLabel()

        for (VisitImpl v : StudyManager.getInstance().getVisits(_study, Visit.Order.SEQUENCE_NUM))
        {
            if (name.equals(v.getLabel()) || (seq != -1 && seq >= v.getSequenceNumMin() && seq <= v.getSequenceNumMax()))
            {
                if (null != visitMatch)
                    return null;        // ambiguous
                visitMatch = v;
                seq = v.getSequenceNumMin();
            }
        }
        if (-1 == seq)
            return null;

        if (visitMatch == null)
            return null;

        // see if we already have a column for this sequence (with a different name)
        ColumnInfo col = _seqColumnMap.get(seq);
        if (col != null)
            return col;
        return createVisitDatasetColumn(name, seq, visitMatch);
    }
}
