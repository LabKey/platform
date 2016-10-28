/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.CohortImpl;

/**
 * User: brittp
 * Date: Sep 10, 2009
 * Time: 10:32:54 AM
 */
public interface CohortFilter
{
    Type getType();
    ActionURL addURLParameters(Study study, ActionURL url, @Nullable String dataregion);
    String getDescription(Container container, User user);
    void addFilterCondition(TableInfo table, Container container, SimpleFilter filter);
    String getCacheKey();

    @Deprecated /** Callers need to handle multiple cohorts case */
    CohortImpl getCohort(Container container, User user);

    @Deprecated /** Callers need to handle multiple cohorts case */
    int getCohortId();

    public enum Type
    {
        PTID_INITIAL("Initial cohort")
        {
            public FieldKey getFilterColumn(Container container)
            {
                return FieldKey.fromParts(StudyService.get().getSubjectColumnName(container), "InitialCohort", "rowid");
            }
        },

        PTID_CURRENT("Current cohort")
        {
            public FieldKey getFilterColumn(Container container)
            {
                return FieldKey.fromParts(StudyService.get().getSubjectColumnName(container), "Cohort", "rowid");
            }
        },

        DATA_COLLECTION("Cohort as of data collection")
        {
            public FieldKey getFilterColumn(Container container)
            {
                return FieldKey.fromParts(StudyService.get().getSubjectVisitColumnName(container), "Cohort", "rowid");
            }
        };

//        ALL("All participants")
//        {
//            public FieldKey getFilterColumn(Container container)
//            {
//                return FieldKey.fromParts(StudyService.get().getSubjectColumnName(container), "Cohort", "rowid");
//            }
//        };


        private String _title;

        Type(String title)
        {
            _title = title;
        }

        public String getTitle()
        {
            return _title;
        }

        public abstract FieldKey getFilterColumn(Container container);
    }
}
