/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.DateUtil;
import org.labkey.study.visitmanager.SequenceVisitManager;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * User: matthewb
 * Date: 2012-10-11
 * Time: 10:49 AM
 */
public class SequenceNumImportHelper
{
    final TimepointType _timetype;
    final Date _startDate;
    final int _startDaysSinceEpoch;
    final Double _defaultSequenceNum;
    final CaseInsensitiveHashMap<String> _translateMap = new CaseInsensitiveHashMap<>();
    final SequenceVisitMap _sequenceNumMap;


    public SequenceNumImportHelper(@NotNull Study study, @Nullable DatasetDefinition def)
    {
        _timetype = study.getTimepointType();
        _startDate = study.getStartDate();
        _startDaysSinceEpoch = null==_startDate?0:convertToDaysSinceEpoch(_startDate);
        if (null != def && (def.isDemographicData() || def.isParticipantAliasDataset()))
            _defaultSequenceNum = VisitImpl.DEMOGRAPHICS_VISIT;
        else
            _defaultSequenceNum = null;
        for (Map.Entry<String, Double> entry : StudyManager.getInstance().getVisitImportMap(study, true).entrySet())
            _translateMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        if (_timetype.isVisitBased())
            _sequenceNumMap = new StudySequenceVisitMap(study);
        else
            _sequenceNumMap = null;
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
        for (Map.Entry<String, Double> entry : visitNameMap.entrySet())
            _translateMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        _sequenceNumMap = sequenceVisitMap;
    }


    public Callable<Object> getCallable(@NotNull final DataIterator it, @Nullable final Integer sequenceIndex, @Nullable final Integer dateIndex)
    {
        return new Callable<Object>()
        {
            @Override
            public Object call() throws Exception
            {
                Object seq = null==sequenceIndex ? null : it.get(sequenceIndex);
                Object d = null==dateIndex ? null : it.get(dateIndex);
                Date date = null;
                try
                {
                    if (null == d || d instanceof Date)
                        date = (Date)d;
                    else
                        date = new Date(DateUtil.parseDateTime(String.valueOf(d)));
                }
                catch (ConversionException x)
                {
                    // DataIterator will catch this an report an error, and we shouldn't usually be getting strings here
                }
                Double sequencenum = translateSequenceNum(seq, date);
                return null==sequencenum ? seq : sequencenum;
            }
        };
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


    private static Date parseDate(String s)
    {
        try
        {
            if (StringUtils.isEmpty(s))
                return null;
            return new Date(DateUtil.parseDate(s));
        }
        catch (ConversionException x)
        {
            return null;
        }
    }



    // we want to be timezone safe here
    static final long epochLocal = DateUtil.parseISODateTime("1970-01-01");

    private static int convertToDaysSinceEpoch(Date d)
    {
        return (int)((d.getTime()-epochLocal) / TimeUnit.DAYS.toMillis(1));
    }


    public Double translateSequenceNum(@Nullable Object seq, @Nullable Object d)
    {
        Double sequencenum = null;
        Date date = null;

        if (null != d)
        {
            if (d instanceof Date)
                date = (Date)d;
            else
                date = parseDate(String.valueOf(d));
        }

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
                sequencenum = parseDouble(_translateMap.get(seq));
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
        if (null != v && v.getSequenceNumHandlingEnum() == Visit.SequenceHandling.logUniqueByDate)
        {
            int daysSinceEpoch = convertToDaysSinceEpoch(date);
            int offset = Math.max(0,daysSinceEpoch - _startDaysSinceEpoch);
            double fraction = offset/10000.0;
            sequencenum += fraction;
        }
        return sequencenum;
    }



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




    /**
     *  TESTS
     **/



    static class TestSequenceVisitMap implements SequenceVisitMap
    {
        @Override
        public Visit get(final Double d)
        {
            return new VisitImpl()
            {
                @Override
                public double getSequenceNumMin()
                {
                    return Math.floor(d);
                }

                @Override
                public @NotNull SequenceHandling getSequenceNumHandlingEnum()
                {
                    return 9999.0 == Math.floor(d) ? SequenceHandling.logUniqueByDate : SequenceHandling.normal;
                }
            };
        }
    }


    static Date parseDateTime(String s)
    {
        return new Date(DateUtil.parseDateTime(s));
    }


    public static class SequenceNumTest extends Assert
    {
        private static final double DELTA = 1E-8;
    
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
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<>();
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
            assertEquals(1.0, h.translateSequenceNum(1.0,null), DELTA);
            assertEquals(1.0, h.translateSequenceNum("Enrollment",null), DELTA);
            assertEquals(9999.0000, h.translateSequenceNum("SR",null), DELTA);
            assertEquals(9999.0000, h.translateSequenceNum(9999.0000,null), DELTA);
            assertEquals(9999.0000, h.translateSequenceNum("9999.0000",null), DELTA);
            assertEquals(9999.0001, h.translateSequenceNum("9999.0001",parseDateTime("1 Jan 2001")), DELTA);
            assertEquals(9999.0000, h.translateSequenceNum("SR",parseDateTime("1 Jan 2000")), DELTA);
            assertEquals(9999.0000, h.translateSequenceNum("SR",parseDateTime("1 Jan 2000 01:00")), DELTA);
            assertEquals(9999.0000, h.translateSequenceNum("SR",parseDateTime("1 Jan 2000 23:00")), DELTA);
            assertEquals(9999.0001, h.translateSequenceNum("SR",parseDateTime("2 Jan 2000")), DELTA);
            assertEquals(9999.0001, h.translateSequenceNum("SR",parseDateTime("2 Jan 2000 01:00")), DELTA);
            assertEquals(9999.0001, h.translateSequenceNum("SR",parseDateTime("2 Jan 2000 23:00")), DELTA);
            assertEquals(9999.0365, h.translateSequenceNum("SR",parseDateTime("31 Dec 2000 23:59:59")), DELTA);
            assertEquals(9999.0366, h.translateSequenceNum("SR",parseDateTime("1 Jan 2001")), DELTA);
        }

        @Test
        public void testVisitBasedDemographic()
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<>();
            map.put("Enrollment",1.0000);
            map.put("SR",9999.0000);
            SequenceNumImportHelper h = new SequenceNumImportHelper(
                    TimepointType.VISIT,
                    parseDateTime("1 Jan 2000 1:00pm"),
                    42.0000,
                    map,
                    new TestSequenceVisitMap()
                    );
            assertEquals(42.0000, h.translateSequenceNum(null,null), DELTA);
            assertEquals(42.0000, h.translateSequenceNum(null, parseDateTime("1 Jan 2010")), DELTA);
            assertEquals(1.0, h.translateSequenceNum(1.0,null), DELTA);
            assertEquals(1.0, h.translateSequenceNum("Enrollment",null), DELTA);
            assertEquals(9999.0000, h.translateSequenceNum("SR",null), DELTA);
        }


        // this test does not run stand-alone because of StudyManager.sequenceNumFromDate(date)
        @Test
        public void testDateBasedNotDemographic()
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<>();
            SequenceNumImportHelper h = new SequenceNumImportHelper(
                    TimepointType.DATE,
                    parseDateTime("1 Jan 2000 1:00pm"),
                    null,
                    map,
                    new TestSequenceVisitMap()
                    );
            assertEquals(-1.0, h.translateSequenceNum(null,null), DELTA);
            assertEquals(20100203.0, h.translateSequenceNum(null, parseDateTime("3 Feb 2010")), DELTA);
            assertEquals(20100203.0, h.translateSequenceNum(null, parseDateTime("3 Feb 2010 1:00")), DELTA);
            assertEquals(20100203.0, h.translateSequenceNum(null, parseDateTime("3 Feb 2010 23:00")), DELTA);
            assertEquals(20100203.0, h.translateSequenceNum(20100203.0,null), DELTA);
        }
    }
}
