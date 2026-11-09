/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.api.studydesign;

import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.studydesign.query.AbstractStudyDesignDomainKind;
import org.labkey.api.studydesign.query.StudyDesignQuerySchema;
import org.labkey.api.studydesign.query.StudyDesignSchema;
import org.labkey.api.studydesign.query.StudyPersonnelDomainKind;
import org.labkey.api.studydesign.query.StudyProductAntigenDomainKind;
import org.labkey.api.studydesign.query.StudyProductDomainKind;
import org.labkey.api.studydesign.query.StudyTreatmentDomainKind;
import org.labkey.api.studydesign.query.StudyTreatmentProductDomainKind;

import java.util.Set;

import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.PERSONNEL_TABLE_NAME;
import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME;
import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.PRODUCT_TABLE_NAME;
import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME;
import static org.labkey.api.studydesign.query.StudyDesignQuerySchema.TREATMENT_TABLE_NAME;

public class StudyDesignManager
{
    private static final StudyDesignManager _instance = new StudyDesignManager();
    public static final String MODULE_NAME = "StudyDesign";

    public static StudyDesignManager get()
    {
        return _instance;
    }

    public boolean isModuleActive(Container c)
    {
        if (c == null)
            return false;

        Module studyDesignModule = ModuleLoader.getInstance().getModule(MODULE_NAME);
        return null != studyDesignModule && c.getActiveModules().contains(studyDesignModule);
    }

    public void deleteStudyDesignData(Container c, Set<TableInfo> deletedTables)
    {
        Filter filter = SimpleFilter.createContainerFilter(c);
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignImmunogenTypes(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignImmunogenTypes());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignChallengeTypes(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignChallengeTypes());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignGenes(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignGenes());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignRoutes(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignRoutes());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignSubTypes(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignSubTypes());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignSampleTypes(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignSampleTypes());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignUnits(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignUnits());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignAssays(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignAssays());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoStudyDesignLabs(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoStudyDesignLabs());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoDoseAndRoute(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoDoseAndRoute());

        Table.delete(StudyDesignSchema.getInstance().getTableInfoTreatmentVisitMap(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoTreatmentVisitMap());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoObjective(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoObjective());

        Table.delete(StudyDesignSchema.getInstance().getTableInfoAssaySpecimenVisit(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoAssaySpecimenVisit());
        Table.delete(StudyDesignSchema.getInstance().getTableInfoAssaySpecimen(), filter);
        deletedTables.add(StudyDesignSchema.getInstance().getTableInfoAssaySpecimen());
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
        String domainURI = domainKind.generateDomainURI(StudyDesignQuerySchema.STUDY_SCHEMA_NAME, tableName, c, null);
        PropertyService.get().ensureDomain(domainKind.getDomainContainer(c), user, domainURI, tableName);
    }
}
