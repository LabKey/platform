/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

package org.labkey.study.importer;

import org.apache.commons.lang.ArrayUtils;
import org.labkey.api.data.ObjectFactory;
import org.labkey.study.model.Visit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.StringTokenizer;


/**
 * User: brittp
 * Date: Jan 7, 2006
 * Time: 3:11:37 PM
 */
class VisitMapRecord
{
    private int _visitRowId = -1;
    private double _sequenceNumberMin = 0;
    private double _sequenceNumberMax = 0;
    private Visit.Type _visitType;
    private String _visitLabel;
    private int _visitDatePlate = -1;
    private String _visitDateField;
    private int _visitDueDay = -1;
    private int _visitOverdueAllowance = -1;
    private int[] _requiredPlates;
    private int[] _optionalPlates;
    private int _missedNotificationPlate = -1;
    private String _terminationWindow;


/*    public VisitMapRecord(String record)
    {
        PipeDelimParser parser = new PipeDelimParser(record);
        _visitNumber = toInt(parser.next());
        _visitType = getType(parser.next());
        _visitLabel = parser.next();
        _visitDatePlate = toInt(parser.next(), -1);
        _visitDateField = parser.next();
        _visitDueDay = toInt(parser.next());
        _visitOverdueAllowance = toInt(parser.next(), -1);
        _requiredPlates = toIntArray(parser.next());
        _optionalPlates = toIntArray(parser.next());
        _missedNotificationPlate = toInt(parser.next(), -1);
        if (parser.hasNext())
            _terminationWindow = parser.next();
    } */


    public VisitMapRecord(Map record)
    {
        init(record);
    }


    private void init(Map record)
    {
        String range = (String)record.get("sequenceRange");
        if (null == range)
            throw new IllegalArgumentException("Sequence range is required");

        String split[] = range.split("[\\-\\~]");
        _sequenceNumberMin = Visit.parseSequenceNum(split[0]);
        if (split.length > 1)
            _sequenceNumberMax = Visit.parseSequenceNum(split[1]);
        else
            _sequenceNumberMax = _sequenceNumberMin;
        _visitType   = getType((String)record.get("visitType"));
        _visitLabel =  (String)record.get("visitLabel");
        _visitDatePlate = defaultInt((Integer)record.get("visitDatePlate"), -1);
        _visitDateField = (String)record.get("visitDateField");
        _visitDueDay =  defaultInt((Integer)record.get("visitDueDay"), -1);
        _visitOverdueAllowance = defaultInt((Integer)record.get("visitDueAllowance"), -1);
        _requiredPlates = toIntArray((String) record.get("requiredPlates"));
        _optionalPlates = toIntArray((String) record.get("optionalPlates"));
        _missedNotificationPlate = defaultInt((Integer)record.get("missedNotificationPlate"), -1);
        _terminationWindow = (String)record.get("terminationWindow");
    }


    private static int defaultInt(Integer i, int defaultInt)
    {
        return null == i ? defaultInt : i;
    }


    public double getSequenceNumMin()           { return _sequenceNumberMin; }
    public double getSequenceNumMax()           { return _sequenceNumberMax; }
    public int getMissedNotificationPlate() { return _missedNotificationPlate; }
    public int[] getOptionalPlates()        { return _optionalPlates; }
    public int[] getRequiredPlates()        { return _requiredPlates; }
    public String getTerminationWindow()    { return _terminationWindow; }
    public String getVisitDateField()       { return _visitDateField; }
    public int getVisitDatePlate()          { return _visitDatePlate; }
    public int getVisitDueDay()             { return _visitDueDay; }
    public String getVisitLabel()           { return _visitLabel; }
    public int getVisitOverdueAllowance()   { return _visitOverdueAllowance; }
    public Visit.Type getVisitType()        { return _visitType; }


/*    private int toInt(String str, int defaultValue)
    {
        if (str.length() == 0)
            return defaultValue;
        try
        {
            return Integer.parseInt(str.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    } */

    private int toInt(String str)
    {
        return Integer.parseInt(str.trim());
    }

    static private int[] emptyIntArray = new int[0];

    private int[] toIntArray(String list)
    {
        if (null == list)
            return emptyIntArray;

        StringTokenizer st = new StringTokenizer(list, ", \t;");
        ArrayList<Integer> values = new ArrayList<Integer>(st.countTokens());
        while (st.hasMoreTokens())
        {
            String s = st.nextToken();
            int index = s.indexOf('~');
            if (index == -1)
            {
                values.add(toInt(s));
            }
            else
            {
                int a = toInt(s.substring(0,index));
                int b = toInt(s.substring(index+1));
                for (int i=a ; i<=b ; i++)
                    values.add(i);
            }
        }
        return ArrayUtils.toPrimitive(values.toArray(new Integer[0]));
    }

    private Visit.Type getType(String str)
    {
        str = str.trim();
        if (str.length() != 1)
            return null;
        else
            return Visit.Type.getByCode(str.charAt(0));
    }


    // set by visit importer
    public void setVisitRowId(int rowId)
    {
        this._visitRowId = rowId;
    }

    public int getVisitRowId()
    {
        return this._visitRowId;
    }


    static
    {
    ObjectFactory.Registry.register(VisitMapRecord.class, new VisitMapRecordFactory());
    }

    // UNDONE: should have BaseObjectFactory to implment handle in terms of fromMap()
    static class VisitMapRecordFactory implements ObjectFactory<VisitMapRecord>
    {
        public void fromMap(VisitMapRecord v, Map<String,Object> m)
        {
            throw new UnsupportedOperationException();
        }

        public VisitMapRecord fromMap(Map<String,? extends Object> m)
        {
            return new VisitMapRecord(m);
        }

        public Map toMap(VisitMapRecord bean, Map m)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        public VisitMapRecord[] handleArray(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }

        public VisitMapRecord handle(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }
    }
}
