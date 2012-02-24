package org.labkey.study.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Feb 23, 2012
 */
public class SpecimenReportQuery
{
    public static final String PIVOT_BY_PRIMARY_TYPE = "SpecimenSummary_PivotByPrimaryType";

    private static final String sql_pivotByPrimaryType = "SELECT\n" +
            "  Container,\n" +
            "  %s,\n" +
            "  SequenceNum,\n" +
            "  %s,\n" +
            "  PrimaryType,\n" +
            "  SUM(VialCount) AS VialCount,\n" +
            "  SUM(LockedInRequestCount) AS LockedInRequestCount,\n" +
            "  SUM(AtRepositoryCount) AS AtRepositoryCount,\n" +
            "  SUM(AvailableCount) AS AvailableCount,\n" +
            "  SUM(ExpectedAvailableCount) AS ExpectedAvailableCount\n" +
            "\n" +
            "FROM SpecimenSummary\n" +
            "\n" +
            "GROUP BY Container, %s, SequenceNum, %s, PrimaryType\n" +
            "\n" +
            "PIVOT VialCount, AvailableCount, AtRepositoryCount, LockedInRequestCount, ExpectedAvailableCount\n" +
            "  BY PrimaryType\n" +
            "  IN (SELECT RowId, Description FROM SpecimenPrimaryType)";

    public static TableInfo getPivotByPrimaryType(Container container, User user)
    {
        Study study = StudyService.get().getStudy(container);

        if (study == null)
            throw new IllegalStateException("A study does not exist for this folder");

        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String visitCol = StudyService.get().getSubjectVisitColumnName(container);

        String query = String.format(sql_pivotByPrimaryType, subjectCol, visitCol, subjectCol, visitCol);

        QueryDefinition qdef = QueryService.get().createQueryDef(user, container, StudyQuerySchema.SCHEMA_NAME, PIVOT_BY_PRIMARY_TYPE);
        qdef.setSql(query);
        qdef.setDescription("Contains up to one row of Specimen Primary Type totals for each " + StudyService.get().getSubjectNounSingular(container) +
            "/visit combination.");

        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo tinfo = qdef.getTable(errors, true);

        if (!errors.isEmpty())
        {
            StringBuilder sb = new StringBuilder();

            for (QueryException qe : errors)
            {
                sb.append(qe.getMessage()).append('\n');
            }
            throw new IllegalStateException(sb.toString());
        }
        return tinfo;
    }
}
