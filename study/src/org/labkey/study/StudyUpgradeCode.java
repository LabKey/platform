/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.study.Study;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 4:54:36 PM
 */
public class StudyUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(StudyUpgradeCode.class);

    /**
     * Move QC state to core to allow it to be used by modules outside the study module.  Moves existing data over
     * as well and handles inserting into identity/serial columns for SQL Server and Postgres.  Drops study.qcstate
     * but references to study.qcstate in the study user schema have been updated to core.qcstate.
     */

    /*
        Note: When this upgrade code is removed (in v20.1), we'll need to move the three ADD CONSTRAINT statements into an upgrade script
     */

    // Invoked by study-17.20-17.30.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void moveQCStateToCore(final ModuleContext context)
    {
        SQLFragment sqlFrag;
        String sql;
        try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            SqlExecutor sel = new SqlExecutor(StudySchema.getInstance().getScope());

            // SQL Server insert into identity column
            if (StudySchema.getInstance().getSqlDialect().isSqlServer())
            {
                sql = "SET IDENTITY_INSERT core.QCState ON";
                sqlFrag = new SQLFragment(sql);
                sel.execute(sqlFrag);
            }

            sql = "INSERT INTO core.QCState (RowId, Label, Description, Container, PublicData) " +
                    "SELECT RowId, Label, Description, Container, PublicData FROM study.QCState;";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            if (StudySchema.getInstance().getSqlDialect().isSqlServer())
            {
                sql = "SET IDENTITY_INSERT core.QCState OFF";
                sqlFrag = new SQLFragment(sql);
                sel.execute(sqlFrag);
            }
            else
            {   // PG after inserting into serial column
                sql = "SELECT setval(pg_get_serial_sequence('core.QCState', 'rowid'), \n" +
                        "(SELECT MAX(RowId) FROM core.QCState));";
                sqlFrag = new SQLFragment(sql);
                sel.execute(sqlFrag);
            }

            sql = "ALTER TABLE study.Study DROP CONSTRAINT FK_Study_DefaultAssayQCState";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study DROP CONSTRAINT FK_Study_DefaultDirectEntryQCState";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study DROP CONSTRAINT FK_Study_DefaultPipelineQCState";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES core.QCState (RowId)";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES core.QCState (RowId)";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES core.QCState (RowId)";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            transaction.commit();
        }

        // Don't hold transaction open for this
        sql = "DROP TABLE study.QCState";
        sqlFrag = new SQLFragment(sql);
        new SqlExecutor(StudySchema.getInstance().getScope()).execute(sqlFrag);
    }

    // Invoked by study-17.30-18.10.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void updateSpecimenHash(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                List<String> updated = new ArrayList<>();
                Set<Container> allContainers = ContainerManager.getAllChildren(ContainerManager.getRoot());
                for (Container c : allContainers)
                {
                    Study study = StudyManager.getInstance().getStudy(c);
                    if (null != study)
                    {
                        _log.info("Updating Specimen.SpecimenHash in container: " + c.getName());
                        TableInfo specimenTable = StudySchema.getInstance().getTableInfoSpecimen(c);
                        SQLFragment sql = new SQLFragment("UPDATE ");
                        sql.append(specimenTable.getSelectName()).append(" SET SpecimenHash = (SELECT \n");
                        SpecimenImporter.makeUpdateSpecimenHashSql(StudySchema.getInstance().getSchema(), c, Collections.emptyList(), "", sql);
                        sql.append(")");
                        new SqlExecutor(StudySchema.getInstance().getSchema().getScope()).execute(sql);
                    }
                }
                transaction.commit();
            }
            catch (Exception e)
            {
                _log.error("Error updating SpecimenHash: " + e.getMessage());
            }
        }
    }
}