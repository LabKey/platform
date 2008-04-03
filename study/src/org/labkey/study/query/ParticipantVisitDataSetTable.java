package org.labkey.study.query;

import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.*;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;
import org.labkey.study.visitmanager.VisitManager;

import javax.servlet.ServletException;
import java.util.*;

public class ParticipantVisitDataSetTable extends VirtualTable
{
    Study _study;
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
        TreeMap<Double,Visit> visitSequenceMap = visitManager.getVisitSequenceMap();
        TreeMap<Integer,Visit> visitRowIdMap = new TreeMap<Integer, Visit>();
        for (Visit v : visitSequenceMap.values())
            visitRowIdMap.put(v.getRowId(), v);

        // visits for this dataset
        // NOTE vdsList (and therefore visitList) is in display order
        List<VisitDataSet> vdsList = _dataset.getVisitDataSets();
        Set<Integer> visitIds = new HashSet<Integer>();
        List<Visit> visitList = new ArrayList<Visit>(vdsList.size());
        for (VisitDataSet vds : vdsList)
        {
            Visit visit = visitRowIdMap.get(vds.getVisitRowId());
            if (null != visit && null != StringUtils.trimToNull(visit.getLabel()))
            {
                visitList.add(visit);
                visitIds.add(vds.getVisitRowId());
            }
        }

        Set<Double> sequenceSet = new TreeSet<Double>();
        for (VisitDataSet vds : vdsList)
        {
            Visit visit = visitRowIdMap.get(vds.getVisitRowId());
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
                Visit visit = visitManager.findVisitBySequence(d);
                if (null != visit && visitIds.add(visit.getRowId()))
                    visitList.add(visit);
            }
            //Resort the visit list
            Collections.sort(visitList, new Comparator<Visit>() {

                public int compare(Visit v1, Visit v2)
                {
                    return v1.getDisplayOrder() != v2.getDisplayOrder() ? v1.getDisplayOrder() - v2.getDisplayOrder() : (int) (v1.getSequenceNumMin() - v2.getSequenceNumMin());
                }
            });
        }

        // duplicate label check a) two visits with same label b) two sequences with same visit
        MultiValueMap labelMap = new MultiValueMap();
        for (double seq : sequenceSet)
        {
            Visit visit = visitManager.findVisitBySequence(seq);
            if (null == visit)
                continue;
            labelMap.put(_schema.decideTableName(visit), seq);
        }

        // nested loop preserves visit display order
        // UNDONE: except that addColumn() does not preserve order
        for (Visit visit : visitList)
        {
            boolean uniqueLabel = labelMap.getCollection(visit.getLabel()).size() == 1;
            boolean hasSequenceRange = visit.getSequenceNumMin() != visit.getSequenceNumMax();

            // add columns for each sequence, show if there is a sequence range
            for (double seq : sequenceSet)
            {
                if (!_inSequence(visit, seq))
                    continue;
                String name = "seq" + Visit.formatSequenceNum(seq);
                String label = visit.getLabel();
                if (!uniqueLabel || hasSequenceRange)
                    label += " (" + Visit.formatSequenceNum(seq) + ")";
                ColumnInfo colSeq = createVisitDataSetColumn(name, seq);
                colSeq.setCaption(label);
//                colSeq.setIsHidden(!hasSequenceRange);
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
                colLabel.setIsHidden(hasSequenceRange);
                addColumn(colLabel);
            }
*/
        }
    }


    private static boolean _inSequence(Visit v, double seq)
    {
        assert v.getSequenceNumMin() >= 0;
        assert v.getSequenceNumMax() >= 0;
        assert v.getSequenceNumMin() <= v.getSequenceNumMax();
        return seq >= v.getSequenceNumMax() && seq <= v.getSequenceNumMax();
    }

    
    protected ColumnInfo createVisitDataSetColumn(String name, final double sequenceNum)
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
        ret.setFk(new ForeignKey() {
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
                    DataSetTable ret = new DataSetTable(_schema, _dataset);
                    ret.hideParticipantLookups();
                    ret.addCondition(new SQLFragment("SequenceNum=" + sequenceNum));
                    return ret;
                }
                catch (ServletException e)
                {
                    return null;
                }
            }

            public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
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
                seq = Visit.parseSequenceNum(name.substring("seq".length()));
            }
            catch (NumberFormatException x)
            {
                //
            }
        }

        Visit visitMatch = null;

        // UNDONE: if/when visits have names use Visit.getName() before Visit.getLabel()

        if (-1 == seq)
        {
            for (Visit v : StudyManager.getInstance().getVisits(_study))
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
        return createVisitDataSetColumn(name, seq);
    }
}
