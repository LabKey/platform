/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
 * User: klum
 * Date: Feb 23, 2012
 */
public class SpecimenReportQuery
{
    public static final String PIVOT_BY_PRIMARY_TYPE = "SpecimenSummary_PivotByPrimaryType";
    public static final String PIVOT_BY_DERIVATIVE_TYPE = "SpecimenSummary_PivotByDerivativeType";
    public static final String PIVOT_BY_REQUESTING_LOCATION = "SpecimenSummary_PivotByRequestingLocation";

    private static final String sql_pivotByPrimaryType = "SELECT\n" +
            "  Container,\n" +
            "  %s,\n" +
            "  Visit,\n" +
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
            "GROUP BY Container, %s, Visit, %s, PrimaryType\n" +
            "\n" +
            "PIVOT VialCount, AvailableCount, AtRepositoryCount, LockedInRequestCount, ExpectedAvailableCount\n" +
            "  BY PrimaryType\n" +
            "  IN (SELECT RowId FROM SpecimenPrimaryType)";

    private static final String sql_pivotByDerivativeType =
            "SELECT\n" +
            "  Container,\n" +
            "  %s,\n" +
            "  Visit,\n" +
            "  %s,\n" +
            "  PivotColumn,\n" +
            "  SUM(VialCount) AS VialCount,\n" +
            "  SUM(LockedInRequestCount) AS LockedInRequestCount,\n" +
            "  SUM(AtRepositoryCount) AS AtRepositoryCount,\n" +
            "  SUM(AvailableCount) AS AvailableCount,\n" +
            "  SUM(ExpectedAvailableCount) AS ExpectedAvailableCount\n" +
            "\n" +
            "FROM (SELECT Container, %s, Visit, %s, ('' || CAST(PrimaryType AS VARCHAR) || '-' || CAST(DerivativeType AS VARCHAR)) AS PivotColumn, VialCount, LockedInRequestCount, AtRepositoryCount, AvailableCount, ExpectedAvailableCount FROM SpecimenSummary) X\n" +
            "\n" +
            "GROUP BY Container, %s, Visit, %s, PivotColumn\n" +
            "\n" +
            "PIVOT VialCount, AvailableCount, AtRepositoryCount, LockedInRequestCount, ExpectedAvailableCount\n" +
            "  BY PivotColumn\n" +
            "  IN (SELECT ('' || CAST(PrimaryType AS VARCHAR) || '-' || CAST(DerivativeType AS VARCHAR)) FROM (SELECT DISTINCT PrimaryType, DerivativeType FROM SpecimenSummary) X)";


    private static final String sql_pivotRequestedByLocation =
            "SELECT \n" +
            " Container, Visit, %s, %s, PivotColumn, COUNT(*) AS RequestedVials\n" +
            "FROM\n" +
            "\n" +
            "(SELECT \n" +
            "  Vial.Container, \n" +
            "  Vial.Visit AS Visit, \n" +
            "  Vial.%s, \n" +
            "  Vial.%s, \n" +
            "  '' || CAST(Vial.PrimaryType AS VARCHAR) || '-' || CAST(Vial.DerivativeType AS VARCHAR) || '-' || CAST(Request.Destination AS VARCHAR) AS PivotColumn,\n" +
            "  Vial.DerivativeType, \n" +
            "  Request.Destination \n" +
            "FROM VialRequest) X\n" +
            "\n" +
            "GROUP BY\n" +
            " Container, Visit, %s, %s, PivotColumn\n" +
            "PIVOT RequestedVials BY PivotColumn\n";
//            UNDONE: do we want a custom IN query?
//            IN (SELECT ...)


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
        qdef.setIsHidden(true);

        List<QueryException> errors = new ArrayList<>();
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

    public static TableInfo getPivotByDerivativeType(Container container, User user)
    {
        Study study = StudyService.get().getStudy(container);

        if (study == null)
            throw new IllegalStateException("A study does not exist for this folder");

        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String visitCol = StudyService.get().getSubjectVisitColumnName(container);

        String query = String.format(sql_pivotByDerivativeType, subjectCol, visitCol, subjectCol, visitCol, subjectCol, visitCol);

        QueryDefinition qdef = QueryService.get().createQueryDef(user, container, StudyQuerySchema.SCHEMA_NAME, PIVOT_BY_DERIVATIVE_TYPE);
        qdef.setSql(query);
        qdef.setIsHidden(true);

        List<QueryException> errors = new ArrayList<>();
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

    public static TableInfo getPivotByRequestingLocation(Container container, User user)
    {
        Study study = StudyService.get().getStudy(container);

        if (study == null)
            throw new IllegalStateException("A study does not exist for this folder");

        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String visitCol = StudyService.get().getSubjectVisitColumnName(container);

        String query = String.format(sql_pivotRequestedByLocation, subjectCol, visitCol, subjectCol, visitCol, subjectCol, visitCol);

        QueryDefinition qdef = QueryService.get().createQueryDef(user, container, StudyQuerySchema.SCHEMA_NAME, PIVOT_BY_REQUESTING_LOCATION);
        qdef.setSql(query);
        qdef.setIsHidden(true);

        List<QueryException> errors = new ArrayList<>();
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
