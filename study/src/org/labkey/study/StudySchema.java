/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:36:43 AM
 */
public class StudySchema
{
    private static StudySchema instance = null;

    public static StudySchema getInstance()
    {
        if (null == instance)
            instance = new StudySchema();

        return instance;
    }

    private StudySchema()
    {
    }

    public DbSchema getSchema()
    {
        return StudyManager.getSchema();
    }

    public String getDatasetSchemaName()
    {
        return "studydataset";
    }


    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoStudy()
    {
        return getSchema().getTable("Study");
    }

    public TableInfo getTableInfoVisit()
    {
        return getSchema().getTable("Visit");
    }

    public TableInfo getTableInfoDataSet()
    {
        return getSchema().getTable("DataSet");
    }

    public TableInfo getTableInfoSite()
    {
        return getSchema().getTable("Site");
    }

    public TableInfo getTableInfoVisitMap()
    {
        return getSchema().getTable("VisitMap");
    }

    public TableInfo getTableInfoStudyData(Study study, User user)
    {
        return new StudyUnionTableInfo(getSchema(), study);
    }

    public TableInfo getTableInfoStudyDataFiltered(Study study, Collection<DataSetDefinition> defs)
    {
        return new StudyUnionTableInfo(getSchema(), study, defs);
    }

    public TableInfo getTableInfoParticipant()
    {
        return getSchema().getTable("Participant");
    }

    public TableInfo getTableInfoParticipantVisit()
    {
        return getSchema().getTable("ParticipantVisit");
    }

    public TableInfo getTableInfoSampleRequest()
    {
        return getSchema().getTable("SampleRequest");
    }

    public TableInfo getTableInfoSampleRequestEvent()
    {
        return getSchema().getTable("SampleRequestEvent");
    }

    public TableInfo getTableInfoSampleRequestRequirement()
    {
        return getSchema().getTable("SampleRequestRequirement");
    }

    public TableInfo getTableInfoSampleRequestActor()
    {
        return getSchema().getTable("SampleRequestActor");
    }

    public TableInfo getTableInfoSampleRequestStatus()
    {
        return getSchema().getTable("SampleRequestStatus");
    }

    public TableInfo getTableInfoSampleRequestSpecimen()
    {
        return getSchema().getTable("SampleRequestSpecimen");
    }

    public TableInfo getTableInfoVial()
    {
        return getSchema().getTable("Vial");
    }

    public TableInfo getTableInfoSpecimen()
    {
        return getSchema().getTable("Specimen");
    }

    public TableInfo getTableInfoSpecimenEvent()
    {
        return getSchema().getTable("SpecimenEvent");
    }

    public TableInfo getTableInfoSpecimenDetail()
    {
        return getSchema().getTable("SpecimenDetail");
    }

    public TableInfo getTableInfoSpecimenSummary()
    {
        return getSchema().getTable("SpecimenSummary");
    }

    public TableInfo getTableInfoSpecimenPrimaryType()
    {
        return getSchema().getTable("SpecimenPrimaryType");
    }
    public TableInfo getTableInfoSpecimenAdditive()
    {
        return getSchema().getTable("SpecimenAdditive");
    }
    public TableInfo getTableInfoSpecimenDerivative()
    {
        return getSchema().getTable("SpecimenDerivative");
    }

    public TableInfo getTableInfoUploadLog()
    {
        return getSchema().getTable("UploadLog");
    }

    public TableInfo getTableInfoPlate()
    {
        return getSchema().getTable("Plate");
    }

    public TableInfo getTableInfoWellGroup()
    {
        return getSchema().getTable("WellGroup");
    }

    public TableInfo getTableInfoWell()
    {
        return getSchema().getTable("Well");
    }

    public TableInfo getTableInfoCohort()
    {
        return getSchema().getTable("Cohort");
    }

    public TableInfo getTableInfoQCState()
    {
        return getSchema().getTable("QCState");
    }

    public TableInfo getTableInfoParticipantView()
    {
        return getSchema().getTable("ParticipantView");
    }

    public TableInfo getTableInfoSpecimenComment()
    {
        return getSchema().getTable("SpecimenComment");
    }

    public TableInfo getTableInfoPrimaryType()
    {
        return getSchema().getTable("SpecimenPrimaryType");
    }

    public TableInfo getTableInfoDerivativeType()
    {
        return getSchema().getTable("SpecimenDerivative");
    }

    public TableInfo getTableInfoAdditiveType()
    {
        return getSchema().getTable("SpecimenAdditive");
    }

    public TableInfo getTableInfoSpecimenVialCount()
    {
        return getSchema().getTable("VialCounts");
    }

    public TableInfo getTableInfoSampleAvailabilityRule()
    {
        return getSchema().getTable("SampleAvailabilityRule");
    }

    public static class StudyUnionTableInfo extends SchemaTableInfo
    {
        Study _study;

        final private String[] _columnNames = {
                "participantid",
                "lsid",
                "sequencenum",
                "sourcelsid",
                "_key",
                "_visitdate",
                "qcstate",
                "participantsequencekey"
        };

        final Set<String> unionColumns = new HashSet<String>(Arrays.asList(_columnNames));
        SQLFragment unionSql;


        public StudyUnionTableInfo(DbSchema schema, Study study)
        {
            this(schema, study, Arrays.asList(StudyManager.getInstance().getDataSetDefinitions(study)));
        }

        public StudyUnionTableInfo(DbSchema schema, Study study, Collection<DataSetDefinition> defs)
        {
            super("StudyData", schema);
            _study = study;
            init(schema, defs);
        }

        public void init(DbSchema schema, Collection<DataSetDefinition> defs)
        {
            SQLFragment sqlf = new SQLFragment();
            int count = 0;
            String unionAll = "";

            for (DataSetDefinition def : defs)
            {
                TableInfo ti = def.getStorageTableInfo();
                if (null == ti)
                    continue;
                count++;
                sqlf.append(unionAll);
                sqlf.append("SELECT '" + def.getEntityId() + "' AS dataset, " + def.getDataSetId() + " AS datasetid");

                // UNDONE: need to change this if propertyname doesn't match storage column name
                String visitPropertyName = def.getVisitDatePropertyName();
                ColumnInfo visitColumn = null==visitPropertyName ? null : ti.getColumn(visitPropertyName);
                if (null != visitPropertyName && (null == visitColumn || visitColumn.getSqlTypeInt() != Types.TIMESTAMP))
                    Logger.getLogger(StudySchema.class).info("Could not find visit column of correct type '" + visitPropertyName + "' in dataset '" + def.getName() + "'");
                if (null != visitColumn && visitColumn.getSqlTypeInt() == Types.TIMESTAMP)
                    sqlf.append(", ").append(visitColumn.getValueSql("D")).append(" AS _visitdate");
                else
                    sqlf.append(", ").append(NullColumnInfo.nullValue(getSqlDialect().getDefaultDateTimeDataType())).append(" AS _visitdate");
                
                for (String column : unionColumns)
                {
                    if ("_visitdate".equalsIgnoreCase(column))
                        continue;
                    sqlf.append(", ").append(ti.getColumn(column).getValueSql("D"));
                }

                sqlf.append(" FROM " + ti.getSelectName() + " D");
                unionAll = ") UNION ALL\n(";
            }

            if (0==count)
            {
                sqlf.append("SELECT '' as dataset, 0 as datasetid");
                for (String column : unionColumns)
                {
                    sqlf.append(", ");
                    if ("qcstate".equalsIgnoreCase(column) || "sequencenum".equalsIgnoreCase(column))
                        sqlf.append("0");
                    else if ("participantid".equalsIgnoreCase(column))
                        sqlf.append("CAST(NULL as VARCHAR)");
                    else if ("_visitdate".equalsIgnoreCase(column))
                        sqlf.append("CAST(NULL AS " + schema.getSqlDialect().getDefaultDateTimeDataType() + ")");
                    else
                        sqlf.append(" NULL");
                    sqlf.append(" AS " + column);
                }
                sqlf.append(" WHERE 0=1");
            }
            
            unionSql = new SQLFragment();
            unionSql.appendComment("<StudyUnionTableInfo>", schema.getSqlDialect());
            if (count > 1)
                unionSql.append("(");
            unionSql.append(sqlf);
            if (count > 1)
                unionSql.append(")");
            unionSql.appendComment("</StudyUnionTableInfo>", schema.getSqlDialect());
            makeColumnInfos(_columnNames);
        }


        @Override
        public String getSelectName()
        {
            return null;
        }


        @Override
        public SQLFragment getFromSQL()
        {
            return unionSql;
        }

        @Override
        public QueryUpdateService getUpdateService()
        {
            return new StudyUpdateService(this);
        }

        private void makeColumnInfos(String[] columnNames)
        {
            TableInfo template = DataSetDefinition.getTemplateTableInfo();

            Set<ColumnInfo> infos = new HashSet<ColumnInfo>();
            for (String name : columnNames)
            {
                ColumnInfo ci = new ColumnInfo(name, this);
                ColumnInfo t = template.getColumn(name);
                if (null != t)
                    ci.setExtraAttributesFrom(t);
                addColumn(ci);
            }

            addColumn(new ColumnInfo("dataset", this));
            addColumn(new ColumnInfo("datasetid", this));
        }

        @Override
        public String toString()
        {
            return "StudyData UNION table";
        }
    }


// may or may not use the QueryUpdateService approach for this.
    static class StudyUpdateService implements QueryUpdateService
    {
        private StudyUnionTableInfo _unionTable;

        StudyUpdateService(StudyUnionTableInfo unionTable)
        {
            _unionTable = unionTable;
        }

        @Override
        public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException, ValidationException
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void setBulkLoad(boolean bulkLoad)
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean isBulkLoad()
        {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
