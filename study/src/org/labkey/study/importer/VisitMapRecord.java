/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.study.Visit.Type;
import org.labkey.study.model.VisitImpl;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;


/**
 * User: brittp
 * Date: Jan 7, 2006
 * Time: 3:11:37 PM
 */
class VisitMapRecord
{
    private int _visitRowId = -1;

    private final BigDecimal _sequenceNumberMin;
    private final BigDecimal _sequenceNumberMax;
    private final @Nullable BigDecimal _protocolDay;
    private final boolean _showByDefault;
    private final Type _visitType;
    private final String _visitLabel;
    private final String _visitDescription;
    private final int _visitDatePlate;
    private final String _visitDateField;
    private final int _visitDueDay;
    private final int _visitOverdueAllowance;
    private final Collection<Integer> _requiredPlates;
    private final Collection<Integer> _optionalPlates;
    private final int _missedNotificationPlate;
    private final String _terminationWindow;
    private final String _cohort;
    private final int _displayOrder;
    private final int _chronologicalOrder;
    private final String _sequenceNumHandling;
    private final List<VisitTagRecord> _visitTagRecords;

    public VisitMapRecord(@NotNull BigDecimal sequenceNumberMin, @NotNull BigDecimal sequenceNumberMax, @Nullable BigDecimal protocolDay, String visitType, String visitLabel, String visitDescription,
                          String cohort, int visitDatePlate, Collection<Integer> requiredPlates, Collection<Integer> optionalPlates, boolean showByDefault,
                          int displayOrder, int chronologicalOrder, String sequenceNumHandling, List<VisitTagRecord> visitTagRecords)
    {
        _sequenceNumberMin = sequenceNumberMin.stripTrailingZeros();
        _sequenceNumberMax = sequenceNumberMax.stripTrailingZeros();
        _protocolDay = null != protocolDay ? protocolDay.stripTrailingZeros() : null;
        _visitType = getType(visitType);
        _visitLabel = visitLabel;
        _visitDescription = visitDescription;
        _cohort = cohort;
        _visitDatePlate = visitDatePlate;
        _requiredPlates = requiredPlates;
        _optionalPlates = optionalPlates;
        _showByDefault = showByDefault;
        _displayOrder = displayOrder;
        _chronologicalOrder = chronologicalOrder;
        _sequenceNumHandling = sequenceNumHandling;

        // These are not currently used
        _visitDateField = null;
        _visitDueDay =  -1;
        _visitOverdueAllowance = -1;
        _missedNotificationPlate = -1;
        _terminationWindow = null;
        _visitTagRecords = visitTagRecords;
    }

    public @NotNull BigDecimal getSequenceNumMin()      { return _sequenceNumberMin; }
    public @NotNull BigDecimal getSequenceNumMax()      { return _sequenceNumberMax; }
    public @Nullable BigDecimal getProtocolDay()        { return _protocolDay; }
    public int getMissedNotificationPlate()             { return _missedNotificationPlate; }
    public Collection<Integer> getOptionalPlates()      { return _optionalPlates; }
    public Collection<Integer> getRequiredPlates()      { return _requiredPlates; }
    public String getTerminationWindow()                { return _terminationWindow; }
    public String getVisitDateField()                   { return _visitDateField; }
    public int getVisitDatePlate()                      { return _visitDatePlate; }
    public int getVisitDueDay()                         { return _visitDueDay; }
    public String getVisitLabel()                       { return _visitLabel; }
    public String getVisitDescription()                 { return _visitDescription; }
    public int getVisitOverdueAllowance()               { return _visitOverdueAllowance; }
    public Type getVisitType()                          { return _visitType; }
    public String getCohort()                           { return _cohort; }
    public boolean isShowByDefault()                    { return _showByDefault; }
    public int getDisplayOrder()                        { return _displayOrder; }
    public int getChronologicalOrder()                  { return _chronologicalOrder; }
    public String getSequenceNumHandling()              { return _sequenceNumHandling; }
    public List<VisitTagRecord> getVisitTagRecords()    { return _visitTagRecords;}

    private Type getType(String str)
    {
        if (null == str)
            return null;

        str = str.trim();
        if (str.length() != 1)
            return null;
        else
            return Type.getByCode(str.charAt(0));
    }


    // set by visit importer
    public void setVisitRowId(int rowId)
    {
        _visitRowId = rowId;
    }

    public int getVisitRowId()
    {
        return _visitRowId;
    }

    @Override
    public String toString()
    {
        return null != _visitLabel ? _visitLabel : VisitImpl.getSequenceString(_sequenceNumberMin, _sequenceNumberMax);
    }

    public static class VisitTagRecord
    {
        private final String _visitTagName;
        private final String _cohortLabel;

        public VisitTagRecord(String visitTagName, String cohortLabel)
        {
            _visitTagName = visitTagName;
            _cohortLabel = cohortLabel;
        }

        public String getVisitTagName()
        {
            return _visitTagName;
        }
        public String getCohortLabel()
        {
            return _cohortLabel;
        }
    }
}
