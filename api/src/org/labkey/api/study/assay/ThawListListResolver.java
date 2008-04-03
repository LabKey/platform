package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.util.DateUtil;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Sep 25, 2007
 */
public class ThawListListResolver extends AbstractParticipantVisitResolver
{
    private TableInfo _tableInfo;
    private Map<String, ParticipantVisit> _cache = new HashMap<String, ParticipantVisit>();
    private final ParticipantVisitResolver _childResolver;

    public ThawListListResolver(Container runContainer, Container targetStudyContainer,
                                Container listContainer, String schemaName, String queryName, User user, ParticipantVisitResolver childResolver) throws ExperimentException
    {
        super(runContainer, targetStudyContainer);
        _childResolver = childResolver;
        UserSchema schema = QueryService.get().getUserSchema(user, listContainer, schemaName);
        if (schema == null)
        {
            throw new ExperimentException("Could not find schema " + schemaName);
        }
        _tableInfo = schema.getTable(queryName, null);
        if (_tableInfo == null)
        {
            throw new ExperimentException("Could not find query " + queryName);
        }
    }

    protected ParticipantVisit resolveParticipantVisit(String specimenID, String participantID, Double visitID, Date date)
    {
        String[] pkNames = _tableInfo.getPkColumnNames();
        assert pkNames.length == 1;
        ColumnInfo col = _tableInfo.getColumn(pkNames[0]);
        Object convertedID = null;
        try
        {
            convertedID = ConvertUtils.convert(specimenID, col.getJavaObjectClass());
        }
        catch (ConversionException e)
        {
            // It's OK, there just won't be a match for this row
            return new ParticipantVisitImpl(specimenID, participantID, visitID, date);
        }

        try
        {
            Map<String, Object>[] rows = Table.selectForDisplay(_tableInfo, Table.ALL_COLUMNS, new SimpleFilter(pkNames[0], convertedID), null, Map.class);
            assert rows.length <= 1;
            if (rows.length == 0)
            {
                return new ParticipantVisitImpl(specimenID, participantID, visitID, date);
            }
            else
            {
                String childSpecimenID = rows[0].get("SpecimenID") == null ? null : rows[0].get("SpecimenID").toString();
                String childParticipantID = rows[0].get("ParticipantID") == null ? null : rows[0].get("ParticipantID").toString();
                Double childVisitID = null;
                if (rows[0].get("VisitID") instanceof Number)
                {
                    childVisitID = ((Number)rows[0].get("VisitID")).doubleValue();
                }
                else if (rows[0].get("VisitID") instanceof String)
                {
                    try
                    {
                        childVisitID = Double.parseDouble((String)rows[0].get("VisitID"));
                    }
                    catch (NumberFormatException e) {}
                }
                Date childDate = null;
                if (rows[0].get("Date") instanceof Date)
                {
                    childDate = (Date)rows[0].get("Date");
                }
                else if (rows[0].get("Date") instanceof String)
                {
                    try
                    {
                        DateUtil.parseDateTime((String)rows[0].get("Date"));
                    }
                    catch (ConversionException e) {}
                }
                return _childResolver.resolve(childSpecimenID, childParticipantID, childVisitID, childDate);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
