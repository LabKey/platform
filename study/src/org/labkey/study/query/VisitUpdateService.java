package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

import java.sql.SQLException;
import java.util.Map;

public class VisitUpdateService extends DefaultQueryUpdateService
{
    public VisitUpdateService(VisitTable table)
    {
        super(table, table.getRealTable());
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        return insertUpdate(user, container, row);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        return insertUpdate(user, container, row);
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws QueryUpdateServiceException, SQLException, InvalidKeyException
    {
        throw new UnsupportedOperationException();
    }

    private Map<String, Object> insertUpdate(User user, Container container, Map<String, Object> newRow) throws ValidationException
    {
        StudyManager studyManager = StudyManager.getInstance();
        Study study = studyManager.getStudy(container);

        if (null == study)
            throw new ValidationException("A study was not found in: " + container.getPath());

        if (!newRow.containsKey("sequencenummin"))
            throw new ValidationException("A visit must include SequenceNumMin");

        VisitImpl visit = VisitImpl.fromMap(newRow, container);
        VisitImpl currentVisit = studyManager.getVisitForSequence(study, visit.getSequenceNumMin());

        if (null != currentVisit)
        {
            visit = VisitImpl.merge(currentVisit, visit, false);
            studyManager.updateVisit(user, visit);
        }
        else
        {
            visit = studyManager.createVisit(study, user, visit);
        }

        return visit.toMap();
    }
}
