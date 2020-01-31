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