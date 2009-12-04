/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.study.Visit;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;
import org.labkey.study.visitmanager.VisitManager;

import java.util.*;

public class ParticipantVisitDataSetTable extends VirtualTable
{
    StudyImpl _study;
    StudyQuerySchema _schema;
    DataSetDefinition _dataset;
    ColumnInfo _colParticipantId;
    Map<Double,ColumnInfo> _seqColumnMap = new HashMap<Double,ColumnInfo>();

    public ParticipantVisitDataSetTable(StudyQuerySchema schema, DataSetDefinition dsd, ColumnInfo colParticipantId)
    {
        super(StudySchema.getInstance().getSchema());
        StudyManager studyManager = StudyManager.getInstance();
        _study = studyManager.getStudy(schema.getContainer());
        _colParticipantId = colParticipantId;
        _dataset = dsd;
        _schema = schema;

        // all visits
        VisitManager visitManager = studyManager.getVisitManager(_study);
        TreeMap<Double, VisitImpl> visitSequenceMap = visitManager.getVisitSequenceMap();
        TreeMap<Integer, VisitImpl> visitRowIdMap = new TreeMap<Integer, VisitImpl>();
        for (VisitImpl v : visitSequenceMap.values())
            visitRowIdMap.put(v.getRowId(), v);

        // visits for this dataset
        // NOTE vdsList (and therefore visitList) is in display order
        List<VisitDataSet> vdsList = _dataset.getVisitDataSets();
        Set<Integer> visitIds = new HashSet<Integer>();
        List<VisitImpl> visitList = new ArrayList<VisitImpl>(vdsList.size());
        for (VisitDataSet vds : vdsList)
        {
            VisitImpl visit = visitRowIdMap.get(vds.getVisitRowId());
            if (null != visit && null != StringUtils.trimToNull(visit.getLabel()))
            {
                visitList.add(visit);
                visitIds.add(vds.getVisitRowId());
            }
        }

        Set<Double> sequenceSet = new TreeSet<Double>();
        for (VisitDataSet vds : vdsList)
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
            Collections.sort(visitList, new Comparator<VisitImpl>() {

                public int compare(VisitImpl v1, VisitImpl v2)
                {
                    return v1.getDisplayOrder() != v2.getDisplayOrder() ? v1.getDisplayOrder() - v2.getDisplayOrder() : (int) (v1.getSequenceNumMin() - v2.getSequenceNumMin());
                }
            });
        }

        // duplicate label check a) two visits with same label b) two sequences with same visit
        MultiMap<String, Double> labelMap = new MultiHashMap<String, Double>();

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
                ColumnInfo colSeq = createVisitDataSetColumn(name, seq, visit);
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
                ColumnInfo colLabel = createVisitDataSetColumn(name, Visit.formatSequenceNum(visit.getSequenceNumMin()));
                colLabel.setHidden(hasSequenceRange);
                addColumn(colLabel);
            }
*/
        }
    }


    private static boolean _inSequence(VisitImpl v, double seq)
    {
        assert v.getSequenceNumMin() <= v.getSequenceNumMax();
        return seq >= v.getSequenceNumMax() && seq <= v.getSequenceNumMax();
    }

    
    protected ColumnInfo createVisitDataSetColumn(String name, final double sequenceNum, @NotNull final VisitImpl visit)
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
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                TableInfo table = getLookupTableInfo();
                if (table == null)
                    return null;
                if (displayField == null)
                {
                    //displayField = table.getTitleColumn();
                    // Data Set tables don't have an interesting title column.
                    return null;
                }
                return LookupColumn.create(parent, table.getColumn("ParticipantId"), table.getColumn(displayField), true);
            }

            public TableInfo getLookupTableInfo()
            {
                try
                {
                    DataSetTable dsTable = new DataSetTable(_schema, _dataset);
                    dsTable.hideParticipantLookups();
                    if (_study.isDateBased())
                    {
                        SQLFragment sequenceSelector = new SQLFragment("SequenceNum = (select pv.sequencenum\n" +
                            "from \n" +
                            "study.participantvisit pv\n" +
                            "where\n" +
                            "pv.participantid = (");

                        sequenceSelector.append(dsTable.getFromTable().toString() + ".participantId");

                        sequenceSelector.append(")\n" +
                            "and\n" +
                            "pv.visitrowid = ?)");

                        sequenceSelector.add(visit.getRowId());

                        dsTable.addCondition(sequenceSelector);
                    }
                    else
                    {
                        dsTable.addCondition(new SQLFragment("SequenceNum=" + sequenceNum));
                    }
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


    public ColumnInfo getColumn(String name)
    {
        return super.getColumn(name);
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

        if (-1 == seq)
        {
            for (VisitImpl v : StudyManager.getInstance().getVisits(_study, Visit.Order.SEQUENCE_NUM))
            {
                if (name.equals(v.getLabel()))
                {
                    if (null != visitMatch)
                        return null;        // ambiguous
                    visitMatch = v;
                    seq = v.getSequenceNumMin();
                }
            }
        }
        if (-1 == seq)
            return null;

        // see if we already have a column for this sequence (with a different name)
        ColumnInfo col = _seqColumnMap.get(seq);
        if (col != null)
            return col;
        return createVisitDataSetColumn(name, seq, visitMatch);
    }
}
