/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitTag;
import org.labkey.study.model.VisitTagMapEntry;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class VisitTagMapQueryUpdateService extends DefaultQueryUpdateService
{
    public VisitTagMapQueryUpdateService(TableInfo queryTable, TableInfo dbTable, Map<String, String> columnMapping)
    {
        super(queryTable, dbTable, columnMapping);
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        checkSingleUse(container, user, row, null);

        try
        {
            return super.insertRow(user, container, row);
        }
        catch (RuntimeSQLException e)
        {
            if (RuntimeSQLException.isConstraintException(e.getSQLException()))
                throw new ValidationException("VisitTagMap may contain only one row for each (VisitTag, Visit, Cohort) combination.");
            throw e;
        }
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        checkSingleUse(container, user, row, oldRow);

        try
        {
            return super.updateRow(user, container, row, oldRow);
        }
        catch (DataIntegrityViolationException e)
        {
            throw new ValidationException("VisitTagMap may contain only one row for each (VisitTag, Visit, Cohort) combination.");
        }
    }

    protected void checkSingleUse(Container container, User user, Map<String, Object> row, @Nullable Map<String, Object> oldRow) throws ValidationException
    {
        String visitTagName = (String)row.get("VisitTag");
        Object cohortObj = row.get("Cohort");
        if (!(cohortObj instanceof Integer))
            return;                 // skip check
        Integer cohortId = (Integer)cohortObj;

        StudyManager studyManager = StudyManager.getInstance();
        Study study = studyManager.getStudy(container);
        if (null == study)
            throw new IllegalStateException("Expected study.");

        // In Dataspace, VisitTags live at the project level
        Study studyForVisitTags = studyManager.getStudyForVisitTag(study);
        VisitTag visitTag = studyManager.getVisitTag(studyForVisitTags, visitTagName);
        if (null != visitTag && visitTag.isSingleUse())
        {
            List<VisitTagMapEntry> visitTagMapEntries = studyManager.getVisitTagMapEntries(study, visitTagName);
            String errorSingleUse = StudyManager.getInstance().checkSingleUseVisitTag(visitTag, cohortId, visitTagMapEntries,
                    null != oldRow ? (Integer)oldRow.get("RowId") : null, container, user);
            if (null != errorSingleUse)
                throw new ValidationException(errorSingleUse);
        }

    }
}
