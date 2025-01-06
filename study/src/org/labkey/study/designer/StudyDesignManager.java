/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.study.designer;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyPersonnelDomainKind;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.studydesign.AbstractStudyDesignDomainKind;
import org.labkey.study.query.studydesign.StudyProductAntigenDomainKind;
import org.labkey.study.query.studydesign.StudyProductDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentProductDomainKind;

import java.util.Set;

import static org.labkey.study.query.StudyQuerySchema.PERSONNEL_TABLE_NAME;
import static org.labkey.study.query.StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME;
import static org.labkey.study.query.StudyQuerySchema.PRODUCT_TABLE_NAME;
import static org.labkey.study.query.StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME;
import static org.labkey.study.query.StudyQuerySchema.TREATMENT_TABLE_NAME;

public class StudyDesignManager
{
    private static final StudyDesignManager _instance = new StudyDesignManager();

    public static StudyDesignManager get()
    {
        return _instance;
    }

    public DbSchema getSchema()
    {
        return StudySchema.getInstance().getSchema();
    }

    public void deleteStudyDesignLookupValues(Container c, Set<TableInfo> deletedTables)
    {
        Filter filter = SimpleFilter.createContainerFilter(c);
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignChallengeTypes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignChallengeTypes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignGenes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignGenes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignRoutes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignRoutes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignSubTypes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignSubTypes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignSampleTypes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignSampleTypes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignUnits(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignUnits());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignAssays(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignAssays());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignLabs(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignLabs());
        Table.delete(StudySchema.getInstance().getTableInfoDoseAndRoute(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoDoseAndRoute());
    }

    // Proactively create the domains at study creation time to avoid problems with lazy creation, #42641
    public void ensureStudyDesignDomains(Container c, User user)
    {
        ensureDomain(new StudyProductDomainKind(), PRODUCT_TABLE_NAME, c, user);
        ensureDomain(new StudyProductAntigenDomainKind(), PRODUCT_ANTIGEN_TABLE_NAME, c, user);
        ensureDomain(new StudyTreatmentProductDomainKind(), TREATMENT_PRODUCT_MAP_TABLE_NAME, c, user);
        ensureDomain(new StudyTreatmentDomainKind(), TREATMENT_TABLE_NAME, c, user);
        ensureDomain(new StudyPersonnelDomainKind(), PERSONNEL_TABLE_NAME, c, user);
    }

    private void ensureDomain(AbstractStudyDesignDomainKind domainKind, String tableName, Container c, User user)
    {
        String domainURI = domainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, tableName, c, null);
        PropertyService.get().ensureDomain(domainKind.getDomainContainer(c), user, domainURI, tableName);
    }
}
