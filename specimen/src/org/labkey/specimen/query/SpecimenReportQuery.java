/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.specimen.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.ArrayList;
import java.util.List;

public class SpecimenReportQuery
{
    public static final String PIVOT_BY_PRIMARY_TYPE = "SpecimenSummary_PivotByPrimaryType";
    public static final String PIVOT_BY_DERIVATIVE_TYPE = "SpecimenSummary_PivotByDerivativeType";
    public static final String PIVOT_BY_REQUESTING_LOCATION = "SpecimenSummary_PivotByRequestingLocation";

    private static final String sql_pivotByPrimaryType = """
        SELECT
          Container,
          %s,
          Visit,
          %s,
          PrimaryType,
          SUM(VialCount) AS VialCount,
          SUM(LockedInRequestCount) AS LockedInRequestCount,
          SUM(AtRepositoryCount) AS AtRepositoryCount,
          SUM(AvailableCount) AS AvailableCount,
          SUM(ExpectedAvailableCount) AS ExpectedAvailableCount
        
        FROM SpecimenSummary
        
        GROUP BY Container, %s, Visit, %s, PrimaryType
        
        PIVOT VialCount, AvailableCount, AtRepositoryCount, LockedInRequestCount, ExpectedAvailableCount
          BY PrimaryType
          IN (SELECT RowId FROM SpecimenPrimaryType)""";

    private static final String sql_pivotByDerivativeType = """
        SELECT
          Container,
          %s,
          Visit,
          %s,
          PivotColumn,
          SUM(VialCount) AS VialCount,
          SUM(LockedInRequestCount) AS LockedInRequestCount,
          SUM(AtRepositoryCount) AS AtRepositoryCount,
          SUM(AvailableCount) AS AvailableCount,
          SUM(ExpectedAvailableCount) AS ExpectedAvailableCount
        
        FROM (SELECT Container, %s, Visit, %s, ('' || CAST(PrimaryType AS VARCHAR) || '-' || CAST(DerivativeType AS VARCHAR)) AS PivotColumn, VialCount, LockedInRequestCount, AtRepositoryCount, AvailableCount, ExpectedAvailableCount FROM SpecimenSummary) X
        
        GROUP BY Container, %s, Visit, %s, PivotColumn
        
        PIVOT VialCount, AvailableCount, AtRepositoryCount, LockedInRequestCount, ExpectedAvailableCount
          BY PivotColumn
          IN (SELECT ('' || CAST(PrimaryType AS VARCHAR) || '-' || CAST(DerivativeType AS VARCHAR)) FROM (SELECT DISTINCT PrimaryType, DerivativeType FROM SpecimenSummary) X)""";


    private static final String sql_pivotRequestedByLocation = """
        SELECT\s
         Container, Visit, %s, %s, PivotColumn, COUNT(*) AS RequestedVials
        FROM
        
        (SELECT\s
          Vial.Container,\s
          Vial.Visit AS Visit,\s
          Vial.%s,\s
          Vial.%s,\s
          '' || CAST(Vial.PrimaryType AS VARCHAR) || '-' || CAST(Vial.DerivativeType AS VARCHAR) || '-' || CAST(Request.Destination AS VARCHAR) AS PivotColumn,
          Vial.DerivativeType,\s
          Request.Destination\s
        FROM VialRequest) X
        
        GROUP BY
         Container, Visit, %s, %s, PivotColumn
        PIVOT RequestedVials BY PivotColumn
        """;
//            UNDONE: do we want a custom IN query?
//            IN (SELECT ...)


    public static TableInfo getPivotByPrimaryType(UserSchema schema, Study study, ContainerFilter cf)
    {
        Container container = schema.getContainer();

        if (study == null)
            throw new IllegalStateException("A study does not exist for this folder");

        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String visitCol = StudyService.get().getSubjectVisitColumnName(container);

        String query = String.format(sql_pivotByPrimaryType, subjectCol, visitCol, subjectCol, visitCol);

        QueryDefinition qdef = QueryService.get().createQueryDef(schema.getUser(), container, schema, PIVOT_BY_PRIMARY_TYPE);
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
            throw new QueryException(sb.toString());
        }
        return tinfo;
    }

    public static TableInfo getPivotByDerivativeType(UserSchema schema, Study study, ContainerFilter cf)
    {
        Container container = schema.getContainer();

        if (study == null)
            throw new IllegalStateException("A study does not exist for this folder");

        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String visitCol = StudyService.get().getSubjectVisitColumnName(container);

        String query = String.format(sql_pivotByDerivativeType, subjectCol, visitCol, subjectCol, visitCol, subjectCol, visitCol);

        QueryDefinition qdef = QueryService.get().createQueryDef(schema.getUser(), container, schema, PIVOT_BY_DERIVATIVE_TYPE);
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

    public static TableInfo getPivotByRequestingLocation(UserSchema schema, Study study, ContainerFilter cf)
    {
        Container container = schema.getContainer();

        if (study == null)
            throw new IllegalStateException("A study does not exist for this folder");

        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String visitCol = StudyService.get().getSubjectVisitColumnName(container);

        String query = String.format(sql_pivotRequestedByLocation, subjectCol, visitCol, subjectCol, visitCol, subjectCol, visitCol);

        QueryDefinition qdef = QueryService.get().createQueryDef(schema.getUser(), container, schema.getSchemaPath(), PIVOT_BY_REQUESTING_LOCATION);
        qdef.setSql(query);
        qdef.setIsHidden(true);

        List<QueryException> errors = new ArrayList<>();
        qdef.setContainerFilter(cf);
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
