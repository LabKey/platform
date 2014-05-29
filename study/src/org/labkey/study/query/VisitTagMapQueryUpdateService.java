package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
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
        return super.insertRow(user, container, row);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        checkSingleUse(container, user, row, oldRow);
        return super.updateRow(user, container, row, oldRow);
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
