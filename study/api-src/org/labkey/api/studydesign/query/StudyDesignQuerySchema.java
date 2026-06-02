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
package org.labkey.api.studydesign.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.studydesign.StudyDesignManager;

import java.util.HashSet;
import java.util.Set;

public class StudyDesignQuerySchema extends SimpleUserSchema implements UserSchema.HasContextualRoles
{
    public static final String STUDY_SCHEMA_NAME = "study";

    // study design provisioned tables
    public static final String PRODUCT_TABLE_NAME = "Product";
    public static final String PRODUCT_ANTIGEN_TABLE_NAME = "ProductAntigen";
    public static final String TREATMENT_PRODUCT_MAP_TABLE_NAME = "TreatmentProductMap";
    public static final String TREATMENT_TABLE_NAME = "Treatment";
    public static final String PERSONNEL_TABLE_NAME = "Personnel";

    // study design tables in the study schema
    public static final String STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME = "StudyDesignImmunogenTypes";
    public static final String STUDY_DESIGN_CHALLENGE_TYPES_TABLE_NAME = "StudyDesignChallengeTypes";
    public static final String STUDY_DESIGN_GENES_TABLE_NAME = "StudyDesignGenes";
    public static final String STUDY_DESIGN_ROUTES_TABLE_NAME = "StudyDesignRoutes";
    public static final String STUDY_DESIGN_SUB_TYPES_TABLE_NAME = "StudyDesignSubTypes";
    public static final String STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME = "StudyDesignSampleTypes";
    public static final String STUDY_DESIGN_UNITS_TABLE_NAME = "StudyDesignUnits";
    public static final String STUDY_DESIGN_ASSAYS_TABLE_NAME = "StudyDesignAssays";
    public static final String STUDY_DESIGN_LABS_TABLE_NAME = "StudyDesignLabs";
    public static final String DOSE_AND_ROUTE_TABLE_NAME = "DoseAndRoute";

    public static final String TREATMENT_VISIT_MAP_TABLE_NAME = "TreatmentVisitMap";
    public static final String OBJECTIVE_TABLE_NAME = "Objective";

    public static final String ASSAY_SPECIMEN_TABLE_NAME = "AssaySpecimen";
    public static final String ASSAY_SPECIMEN_VISIT_TABLE_NAME = "AssaySpecimenVisit";

    protected Study _study;
    private final Role _contextualRole;
    private final UserSchema _parentSchema;

    // study design tables in the study schema that can exist without a study
    private static final Set<String> ALWAYS_AVAILABLE_TABLES = Set.of(
            STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME,
            STUDY_DESIGN_CHALLENGE_TYPES_TABLE_NAME,
            STUDY_DESIGN_GENES_TABLE_NAME,
            STUDY_DESIGN_ROUTES_TABLE_NAME,
            STUDY_DESIGN_SUB_TYPES_TABLE_NAME,
            STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME,
            STUDY_DESIGN_UNITS_TABLE_NAME,
            STUDY_DESIGN_ASSAYS_TABLE_NAME,
            STUDY_DESIGN_LABS_TABLE_NAME,
            DOSE_AND_ROUTE_TABLE_NAME
    );

    private static final Set<String> STUDY_AVAILABLE_TABLES = Set.of(
            PRODUCT_TABLE_NAME,
            PRODUCT_ANTIGEN_TABLE_NAME,
            TREATMENT_PRODUCT_MAP_TABLE_NAME,
            TREATMENT_TABLE_NAME,
            PERSONNEL_TABLE_NAME,
            TREATMENT_VISIT_MAP_TABLE_NAME,
            OBJECTIVE_TABLE_NAME,
            ASSAY_SPECIMEN_TABLE_NAME,
            ASSAY_SPECIMEN_VISIT_TABLE_NAME
    );

    private StudyDesignQuerySchema(UserSchema studySchema, Study study, @Nullable Role contextualRole)
    {
        super(studySchema.getName(), studySchema.getDescription(), studySchema.getUser(), studySchema.getContainer(),
                studySchema.getDbSchema(), null, getAvailableTables(study), null);

        _parentSchema = studySchema;
        _study = study;
        _contextualRole = contextualRole;
    }

    @Nullable
    public static StudyDesignQuerySchema get(UserSchema userSchema, Container c, @Nullable Study study, @Nullable Role contextualRole)
    {
        return StudyDesignManager.get().isModuleActive(c)
                ? new StudyDesignQuerySchema(userSchema, study, contextualRole)
                : null;
    }

    @Nullable
    public static StudyDesignQuerySchema get(Study study, User user)
    {
        UserSchema schema = StudyService.get().getStudyQuerySchema(study, user);
        if (schema != null)
            return get(schema, schema.getContainer(), study, null);
        return null;
    }

    public UserSchema getParentSchema()
    {
        return _parentSchema;
    }

    private static Set<String> getAvailableTables(Study study)
    {
        Set<String> names = new HashSet<>(ALWAYS_AVAILABLE_TABLES);
        if (study != null)
            names.addAll(STUDY_AVAILABLE_TABLES);

        return names;
    }

    @Override
    public @Nullable TableInfo createTable(String name, ContainerFilter cf)
    {
        // always expose the study designer lookup tables
        if (STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignImmunogenTypesTable(this, cf);
        }
        if (STUDY_DESIGN_CHALLENGE_TYPES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignChallengeTypesTable(this, cf);
        }
        if (STUDY_DESIGN_GENES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignGenesTable(this, cf);
        }
        if (STUDY_DESIGN_ROUTES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignRoutesTable(this, cf);
        }
        if (STUDY_DESIGN_SUB_TYPES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignSubTypesTable(this, cf);
        }
        if (STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignSampleTypesTable(this, cf);
        }
        if (STUDY_DESIGN_UNITS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignUnitsTable(this, cf);
        }
        if (STUDY_DESIGN_ASSAYS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignAssaysTable(this, cf);
        }
        if (STUDY_DESIGN_LABS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignLabsTable(this, cf);
        }
        if (DOSE_AND_ROUTE_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new DoseAndRouteTable(this, cf);
        }

        if (_study == null)
            return null;

        if (PRODUCT_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyProductDomainKind domainKind = new StudyProductDomainKind();
            Domain domain = ensureDomain(domainKind, PRODUCT_TABLE_NAME);

            return StudyProductTable.create(domain, this, isDataspaceProject() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (PRODUCT_ANTIGEN_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyProductAntigenDomainKind domainKind = new StudyProductAntigenDomainKind();
            Domain domain = ensureDomain(domainKind, PRODUCT_ANTIGEN_TABLE_NAME);

            return StudyProductAntigenTable.create(domain, this, isDataspaceProject() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (TREATMENT_PRODUCT_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyTreatmentProductDomainKind domainKind = new StudyTreatmentProductDomainKind();
            Domain domain = ensureDomain(domainKind, StudyDesignQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);

            return StudyTreatmentProductTable.create(domain, this, isDataspace() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (TREATMENT_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyTreatmentDomainKind domainKind = new StudyTreatmentDomainKind();
            Domain domain = ensureDomain(domainKind, TREATMENT_TABLE_NAME);

            return StudyTreatmentTable.create(domain, this, isDataspace() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (PERSONNEL_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyPersonnelDomainKind domainKind = new StudyPersonnelDomainKind();
            Domain domain = ensureDomain(domainKind, PERSONNEL_TABLE_NAME);

            // TODO ContainerFilter
            return StudyPersonnelTable.create(domain, this, isDataspaceProject() ? ContainerFilter.Type.Project.create(this) : null);
        }
        if (TREATMENT_VISIT_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyTreatmentVisitMapTable(this, cf);
        }
        if (OBJECTIVE_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyObjectiveTable(this, cf);
        }
        if (ASSAY_SPECIMEN_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new AssaySpecimenTable(this, cf);
        }
        if (ASSAY_SPECIMEN_VISIT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new AssaySpecimenVisitTable(this, cf);
        }

        return null;
    }

    @NotNull
    private Domain ensureDomain(AbstractStudyDesignDomainKind kind, String tableName)
    {
        Domain result = kind.getDomain(getContainer(), tableName);
        if (result == null)
        {
            throw new IllegalStateException("Could not find a domain for " + tableName + " in " + getContainer().getPath());
        }
        return result;
    }

    /**
     * This only gets overridden in the DataspaceQuerySchema, we should be able to delete this and other support
     * for dataspace in the study design schema when we confirm there are no clients who use that combination.
     */
    public boolean isDataspace()
    {
        return false;
    }

    public boolean isDataspaceProject()
    {
        Container container = getContainer();
        if (null != container)
        {
            Container project = container.getProject();
            if (null != project && project.isDataspace())
                return true;
        }
        return false;
    }

    @Override
    public @NotNull Set<Role> getContextualRoles()
    {
        return null != _contextualRole ? Set.of(_contextualRole) : Set.of();
    }
}
