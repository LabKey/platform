package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.DateUtil;
import org.labkey.study.visitmanager.SequenceVisitManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-10-11
 * Time: 10:49 AM
 */
public class SequenceNumImportHelper
{
    final TimepointType _timetype;
    final Date _startDate;
    int _startDaysSinceEpoch;
    Double _defaultSequenceNum = null;
    Map<String,String> _translateMap = null;
    SequenceVisitMap _sequenceNumMap = null;


    public SequenceNumImportHelper(@NotNull Study study, @Nullable DataSetDefinition def)
    {
        _timetype = study.getTimepointType();
        _startDate = study.getStartDate();
        _startDaysSinceEpoch = null==_startDate?0:convertToDaysSinceEpoch(_startDate);
        if (null != def && def.isDemographicData())
            _defaultSequenceNum =  VisitImpl.DEMOGRAPHICS_VISIT;
        for (Map.Entry<String, Double> entry : StudyManager.getInstance().getVisitImportMap(study, true).entrySet())
            _translateMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        _sequenceNumMap = new StudySequenceVisitMap(study);
    }


    // for testing
    public SequenceNumImportHelper(
            TimepointType timetype,
            Date startDate,
            @Nullable Double defaultSeqNum,
            Map<String,Double> visitNameMap,
            @Nullable SequenceVisitMap sequenceVisitMap)
    {
        _timetype = timetype;
        _startDate = startDate;
        _startDaysSinceEpoch = null==_startDate?0:convertToDaysSinceEpoch(_startDate);
        _defaultSequenceNum = defaultSeqNum;
        _translateMap = new HashMap<String, String>(visitNameMap.size() * 2);
        for (Map.Entry<String, Double> entry : visitNameMap.entrySet())
            _translateMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        _sequenceNumMap = sequenceVisitMap;
    }


    private static Double parseDouble(String s)
    {
        try
        {
            if (StringUtils.isEmpty(s))
                return null;
            return Double.parseDouble(s);
        }
        catch (NumberFormatException x)
        {
            return null;
        }
    }


    // we want to be timezone safe here
    static final long epochLocal = DateUtil.parseDate("1970-01-01");

    private static int convertToDaysSinceEpoch(Date d)
    {
        return (int)((d.getTime()-epochLocal) / TimeUnit.DAYS.toMillis(1));
    }


    Double translateSequenceNum(@Nullable Object seq, @Nullable Date date)
    {
        Double sequencenum = null;

translateToDouble:
        {
            if (seq instanceof Number)
            {
                sequencenum = ((Number)seq).doubleValue();
                break translateToDouble;
            }
            if (seq instanceof String)
            {
                sequencenum = parseDouble((String)seq);
                if (null != sequencenum)
                    break translateToDouble;
                sequencenum = parseDouble(_translateMap.get((String)seq));
                if (null != sequencenum)
                    break translateToDouble;
            }
            sequencenum = _defaultSequenceNum;
            if (null != sequencenum)
                break translateToDouble;

            if (!_timetype.isVisitBased())
            {
                if (null != date)
                    sequencenum = StudyManager.sequenceNumFromDate(date);
                else
                    sequencenum =  VisitImpl.DEMOGRAPHICS_VISIT;
                return sequencenum;
            }
        }

        // TODO : implement optional catch-all visit
        if (null == sequencenum || null == date || null == _sequenceNumMap || null == _startDate)
            return sequencenum;
        // check if there is a fractional part of the sequencenum
        if (sequencenum - Math.floor(sequencenum) != 0.0)
            return sequencenum;

        // handle log-type events which can be unique'd by date
        Visit v = _sequenceNumMap.get(sequencenum);
        // UNDONE 9999 for testing
        boolean appendDateFraction = (null != v && v.getSequenceNumMin() == 9999.0000);
        if (appendDateFraction)
        {
            int daysSinceEpoch = convertToDaysSinceEpoch(date);
            int offset = daysSinceEpoch - _startDaysSinceEpoch;
            double fraction = offset/10000.0;
            sequencenum += fraction;
        }
        return sequencenum;
    }

    /*
     * TESTS
     */

    // for testablity wrap VisitManager in a simple interface

    interface SequenceVisitMap
    {
        Visit get(Double d);
    }


    static class StudySequenceVisitMap implements SequenceVisitMap
    {
        final SequenceVisitManager _svm;
        StudySequenceVisitMap(Study study)
        {
            _svm = (SequenceVisitManager)StudyManager.getInstance().getVisitManager((StudyImpl)study);
        }

        @Override
        public Visit get(Double seq)
        {
            return _svm.findVisitBySequence(seq);
        }
    }


    static class TestSequenceVisitMap implements SequenceVisitMap
    {
        @Override
        public Visit get(Double d)
        {
            if (d == 9999.0000)
            {
                return new VisitImpl()
                {
                    @Override
                    public double getSequenceNumMin()
                    {
                        return 9999.0000;
                    }
                };
            }
            return null;
        }
    }


    static Date parseDateTime(String s)
    {
        return new Date(DateUtil.parseDateTime(s));
    }


    public static class SequenceNumTest extends Assert
    {
        @Test
        public void testEpoch()
        {
            assertEquals(0, convertToDaysSinceEpoch(parseDateTime("1 Jan 1970")));
            assertEquals(0, convertToDaysSinceEpoch(parseDateTime("1 Jan 1970 0:00")));
            assertEquals(0, convertToDaysSinceEpoch(parseDateTime("1 Jan 1970 1:00")));
            assertEquals(0, convertToDaysSinceEpoch(parseDateTime("1 Jan 1970 23:59:59")));
        }

        @Test
        public void testVisitBasedNonDemographic()
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<Double>();
            map.put("Enrollment",1.0000);
            map.put("SR",9999.0000);
            SequenceNumImportHelper h = new SequenceNumImportHelper(
                    TimepointType.VISIT,
                    parseDateTime("1 Jan 2000 1:00pm"),
                    null,
                    map,
                    new TestSequenceVisitMap()
                    );
            assertEquals(null, h.translateSequenceNum(null,null));
            assertEquals(null, h.translateSequenceNum(null, parseDateTime("1 Jan 2010")));
            assertEquals(1.0, h.translateSequenceNum(1.0,null));
            assertEquals(1.0, h.translateSequenceNum("Enrollment",null));
            assertEquals(9999.0000, h.translateSequenceNum("SR",null));
            assertEquals(9999.0000, h.translateSequenceNum(9999.0000,null));
            assertEquals(9999.0000, h.translateSequenceNum("9999.0000",null));
            assertEquals(9999.0001, h.translateSequenceNum("9999.0001",parseDateTime("1 Jan 2001")));
            assertEquals(9999.0000, h.translateSequenceNum("SR",parseDateTime("1 Jan 2000")));
            assertEquals(9999.0000, h.translateSequenceNum("SR",parseDateTime("1 Jan 2000 01:00")));
            assertEquals(9999.0000, h.translateSequenceNum("SR",parseDateTime("1 Jan 2000 23:00")));
            assertEquals(9999.0001, h.translateSequenceNum("SR",parseDateTime("2 Jan 2000")));
            assertEquals(9999.0001, h.translateSequenceNum("SR",parseDateTime("2 Jan 2000 01:00")));
            assertEquals(9999.0001, h.translateSequenceNum("SR",parseDateTime("2 Jan 2000 23:00")));
            assertEquals(9999.0365, h.translateSequenceNum("SR",parseDateTime("31 Dec 2000 23:59:59")));
            assertEquals(9999.0366, h.translateSequenceNum("SR",parseDateTime("1 Jan 2001")));
        }

        @Test
        public void testVisitBasedDemographic()
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<Double>();
            map.put("Enrollment",1.0000);
            map.put("SR",9999.0000);
            SequenceNumImportHelper h = new SequenceNumImportHelper(
                    TimepointType.VISIT,
                    parseDateTime("1 Jan 2000 1:00pm"),
                    42.0000,
                    map,
                    new TestSequenceVisitMap()
                    );
            assertEquals(42.0000, h.translateSequenceNum(null,null));
            assertEquals(42.0000, h.translateSequenceNum(null, parseDateTime("1 Jan 2010")));
            assertEquals(1.0, h.translateSequenceNum(1.0,null));
            assertEquals(1.0, h.translateSequenceNum("Enrollment",null));
            assertEquals(9999.0000, h.translateSequenceNum("SR",null));
        }


        // this test does not run stand-alone because of StudyManager.sequenceNumFromDate(date)
        @Test
        public void testDateBasedNotDemographic()
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<Double>();
            SequenceNumImportHelper h = new SequenceNumImportHelper(
                    TimepointType.DATE,
                    parseDateTime("1 Jan 2000 1:00pm"),
                    null,
                    map,
                    new TestSequenceVisitMap()
                    );
            assertEquals(-1.0, h.translateSequenceNum(null,null));
            assertEquals(20100203.0, h.translateSequenceNum(null, parseDateTime("3 Feb 2010")));
            assertEquals(20100203.0, h.translateSequenceNum(null, parseDateTime("3 Feb 2010 1:00")));
            assertEquals(20100203.0, h.translateSequenceNum(null, parseDateTime("3 Feb 2010 23:00")));
            assertEquals(20100203.0, h.translateSequenceNum(20100203.0,null));
        }
    }
}
