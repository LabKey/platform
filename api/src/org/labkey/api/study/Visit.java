/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

package org.labkey.api.study;

import org.apache.commons.lang3.StringUtils;

/**
 * User: kevink
 * Date: May 27, 2009
 */
public interface Visit extends StudyEntity
{
    String getSequenceString();

    Type getType();

    Integer getVisitDateDatasetId();

    double getSequenceNumMin();

    double getSequenceNumMax();

    Double getProtocolDay();

    Integer getCohortId();

    Integer getId();

    Cohort getCohort();

    int getChronologicalOrder();

    void setChronologicalOrder(int chronologicalOrder);

    SequenceHandling getSequenceNumHandlingEnum();

    String getDescription();

    public enum Type
    {
        SCREENING('X', "Screening"),
        PREBASELINE('P', "Scheduled pre-baseline visit"),
        BASELINE('B', "Baseline"),
        SCHEDULED_FOLLOWUP('S', "Scheduled follow-up"),
        OPTIONAL_FOLLOWUP('O', "Optional follow-up"),
        REQUIRED_BY_NEXT_VISIT('r', "Required by time of next visit"),
        CYCLE_TERMINATION('T', "Cycle termination visit"),
        REQUIRED_BY_TERMINATION('R', "Required by time of termination visit"),
        EARLY_CYCLE_TERMINATION('E', "Early termination of current cycle"),
        ABORT_ALL_CYCLES('A', "Abort all cycles"),
        FINAL_VISIT('F', "Final visit (terminates all cycles)"),
        STUDY_TERMINATION_WINDOW('W', "Study termination window");

        private char _code;
        private String _meaning;

        private Type(char code, String meaning)
        {
            _code = code;
            _meaning = meaning;
        }

        public char getCode()
        {
            return _code;
        }

        public String getMeaning()
        {
            return _meaning;
        }

        public static Type getByCode(char c)
        {
            for (Type type : values())
            {
                if (c == type.getCode())
                    return type;
            }
            return null;
        }
    }

    public enum Order
    {
        CHRONOLOGICAL("ChronologicalOrder,SequenceNumMin"),
        DISPLAY("DisplayOrder,SequenceNumMin"),
        SEQUENCE_NUM("SequenceNumMin");

        private String _sortColumns;

        Order(String sortColumn)
        {
            _sortColumns = sortColumn;
        }

        public String getSortColumns()
        {
            return _sortColumns;
        }
    }

    public enum SequenceHandling
    {
        normal,             // as determined by TimepointType
        logUniqueByDate    // append days since start of study in factional part of sequencenum
        ;

        static SequenceHandling from(String s)
        {
            if (StringUtils.isEmpty(s))
                return normal;
            try
            {
                return valueOf(s);
            }
            catch (IllegalArgumentException x)
            {
                return normal;
            }
        }
    }
}
