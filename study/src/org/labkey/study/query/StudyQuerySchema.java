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

package org.labkey.study.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.specimen.SpecimenManager;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.api.specimen.query.SpecimenPivotByDerivativeType;
import org.labkey.api.specimen.query.SpecimenPivotByPrimaryType;
import org.labkey.api.specimen.query.SpecimenPivotByRequestingLocation;
import org.labkey.api.specimen.query.SpecimenQueryView;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.model.ParticipantGroup;
import org.labkey.api.study.writer.AbstractContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.studydesign.DoseAndRouteTable;
import org.labkey.study.query.studydesign.StudyDesignAssaysTable;
import org.labkey.study.query.studydesign.StudyDesignChallengeTypesTable;
import org.labkey.study.query.studydesign.StudyDesignGenesTable;
import org.labkey.study.query.studydesign.StudyDesignImmunogenTypesTable;
import org.labkey.study.query.studydesign.StudyDesignLabsTable;
import org.labkey.study.query.studydesign.StudyDesignRoutesTable;
import org.labkey.study.query.studydesign.StudyDesignSampleTypesTable;
import org.labkey.study.query.studydesign.StudyDesignSubTypesTable;
import org.labkey.study.query.studydesign.StudyDesignUnitsTable;
import org.labkey.study.query.studydesign.StudyProductAntigenDomainKind;
import org.labkey.study.query.studydesign.StudyProductAntigenTable;
import org.labkey.study.query.studydesign.StudyProductDomainKind;
import org.labkey.study.query.studydesign.StudyProductTable;
import org.labkey.study.query.studydesign.StudyTreatmentDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentProductDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentProductTable;
import org.labkey.study.query.studydesign.StudyTreatmentTable;
import org.labkey.study.query.studydesign.StudyTreatmentVisitMapTable;
import org.labkey.study.visualization.StudyVisualizationProvider;
import org.springframework.validation.BindException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class StudyQuerySchema extends UserSchema
{
    public static final String EXPERIMENTAL_STUDY_SUBSCHEMAS = "StudySubSchemas";

    public static final String SCHEMA_NAME = "study";
    public static final String SCHEMA_DESCRIPTION = "Contains all data related to the study, including subjects, cohorts, visits, datasets, specimens, etc.";
    public static final String SIMPLE_SPECIMEN_TABLE_NAME = "SimpleSpecimen";
    public static final String SPECIMEN_DETAIL_TABLE_NAME = "SpecimenDetail";
    public static final String SPECIMEN_WRAP_TABLE_NAME = "SpecimenWrap";
    public static final String SPECIMEN_EVENT_TABLE_NAME = "SpecimenEvent";
    public static final String SPECIMEN_SUMMARY_TABLE_NAME = "SpecimenSummary";
    public static final String PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME = "ParticipantGroupCohortUnion";
    public static final String LOCATION_SPECIMEN_LIST_TABLE_NAME = "LocationSpecimenList";
    public static final String LOCATION_TABLE_NAME = "Location";
    public static final String SPECIMEN_PRIMARY_TYPE_TABLE_NAME = "SpecimenPrimaryType";
    public static final String SPECIMEN_DERIVATIVE_TABLE_NAME = "SpecimenDerivative";
    public static final String SPECIMEN_ADDITIVE_TABLE_NAME = "SpecimenAdditive";
    public static final String VIAL_TABLE_NAME = "Vial";

    public static final String STUDY_TABLE_NAME = "Study";
    public static final String PROPERTIES_TABLE_NAME = "StudyProperties";
    public static final String STUDY_SNAPSHOT_TABLE_NAME = "StudySnapshot";

    // study design tables that appear in study folders
    public static final String OBJECTIVE_TABLE_NAME = "Objective";
    public static final String PERSONNEL_TABLE_NAME = "Personnel";
    public static final String VISIT_TABLE_NAME = "Visit";
    public static final String VISIT_TAG_TABLE_NAME = "VisitTag";
    public static final String VISIT_TAG_MAP_TABLE_NAME = "VisitTagMap";
    public static final String VISIT_ALIASES = "VisitAliases";
    public static final String ASSAY_SPECIMEN_TABLE_NAME = "AssaySpecimen";
    public static final String ASSAY_SPECIMEN_VISIT_TABLE_NAME = "AssaySpecimenVisit";
    public static final String VISUALIZATION_VISIT_TAG_TABLE_NAME = "VisualizationVisitTag";
    public static final String VISIT_MAP_TABLE_NAME = "VisitMap";

    // extensible study design tables
    public static final String STUDY_DESIGN_SCHEMA_NAME = "studydesign";
    public static final String PRODUCT_TABLE_NAME = "Product";
    public static final String PRODUCT_ANTIGEN_TABLE_NAME = "ProductAntigen";
    public static final String TREATMENT_TABLE_NAME = "Treatment";
    public static final String TREATMENT_PRODUCT_MAP_TABLE_NAME = "TreatmentProductMap";
    public static final String TREATMENT_VISIT_MAP_TABLE_NAME = "TreatmentVisitMap";

    // study design tables that appear in all folders (?)
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

    @Nullable // if no study defined in this container
    final StudyImpl _study;

    final boolean _mustCheckPermissions;
    private boolean _dontAliasColumns = false;

    private Map<Integer, List<BigDecimal>> _datasetSequenceMap;
    public static final String STUDY_DATA_TABLE_NAME = "StudyData";
    public static final String QCSTATE_TABLE_NAME = "QCState";
    protected Set<String> _tableNames;

    private ParticipantGroup _sessionParticipantGroup;


    public StudyQuerySchema(@NotNull StudyImpl study, User user, boolean mustCheckPermissions)
    {
        this(study, study.getContainer(), user, mustCheckPermissions);

        initSessionParticipantGroup(study, user);
    }

    /**
     * This c-tor is for schemas that have no study defined -- _study is null!
     */
    private StudyQuerySchema(@Nullable StudyImpl study, Container c, User user, boolean mustCheckPermissions)
    {
        this(SchemaKey.fromParts(SCHEMA_NAME), SCHEMA_DESCRIPTION, study, c, user, mustCheckPermissions);
    }

    /**
     * This c-tor is for nested study schemas
     */
    protected StudyQuerySchema(SchemaKey path, String description, @Nullable StudyImpl study, Container c, User user, boolean mustCheckPermissions)
    {
        super(path, description, user, c, StudySchema.getInstance().getSchema(), null);
        _study = study;
        _mustCheckPermissions = mustCheckPermissions;
    }


    public static StudyQuerySchema createSchema(StudyImpl study, @NotNull User user, boolean mustCheckPermissions)
    {
        if (null == study)
            throw new NotFoundException("Study not found.");
        if (study.isDataspaceStudy())
            return new DataspaceQuerySchema(study, user, mustCheckPermissions);
        else
            return new StudyQuerySchema(study, user, mustCheckPermissions);
    }


    public static StudyQuerySchema createSchemaWithoutStudy(Container c, User u)
    {
        return new StudyQuerySchema(null, c, u, true);
    }

    @Override
    public Set<String> getSchemaNames()
    {
        LinkedHashSet<String> names = new LinkedHashSet<>(super.getSchemaNames());
        names.addAll(getSubSchemaNames());
        return names;
    }

    protected Set<String> getSubSchemaNames()
    {
        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_STUDY_SUBSCHEMAS))
            return new LinkedHashSet<>(Arrays.asList("Datasets","Design","Specimens"));
        return Collections.emptySet();
    }

    @Override
    public QuerySchema getSchema(String name)
    {
        if (StringUtils.equalsIgnoreCase("Datasets",name))
            return new DatasetSchema(this);
        if (StringUtils.equalsIgnoreCase("Design",name))
            return new DesignSchema(this);
        if (StringUtils.equalsIgnoreCase("Specimens",name))
            return new SpecimenSchema(this);
        return super.getSchema(name);
    }

    public String getSubjectColumnName()
    {
        if (null != _study)
            return _study.getSubjectColumnName();
        return "ParticipantId";
    }


    @Override
    protected boolean canReadSchema()
    {
        SecurityLogger.indent("StudyQuerySchema.canReadSchema()");
        try
        {
            if (!getMustCheckPermissions())
            {
                SecurityLogger.log("getMustCheckPermissions()==false", getUser(), null, true);
                return true;
            }
            return super.canReadSchema();
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    @Override
    public Set<String> getTableNames()
    {
        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_STUDY_SUBSCHEMAS))
            return Collections.emptySet();

        if (_tableNames == null)
        {
            Set<String> names = new LinkedHashSet<>();

            // Always add StudyProperties and study designer lookup tables, even if we have no study
            names.add(PROPERTIES_TABLE_NAME);
            names.add(STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME);
            names.add(STUDY_DESIGN_CHALLENGE_TYPES_TABLE_NAME);
            names.add(STUDY_DESIGN_GENES_TABLE_NAME);
            names.add(STUDY_DESIGN_ROUTES_TABLE_NAME);
            names.add(STUDY_DESIGN_SUB_TYPES_TABLE_NAME);
            names.add(STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME);
            names.add(STUDY_DESIGN_UNITS_TABLE_NAME);
            names.add(STUDY_DESIGN_ASSAYS_TABLE_NAME);
            names.add(STUDY_DESIGN_LABS_TABLE_NAME);
            names.add(DOSE_AND_ROUTE_TABLE_NAME);

            if (_study != null)
            {
                StudyService studyService = StudyService.get();
                if (null == studyService)
                    throw new IllegalStateException("No StudyService!");

                // All these require studies defined
                names.add(STUDY_DATA_TABLE_NAME);
                names.add(studyService.getSubjectTableName(getContainer()));
                names.add(LOCATION_TABLE_NAME);
                if (_study.getTimepointType() != TimepointType.CONTINUOUS)
                {
                    names.add(VISIT_TABLE_NAME);
                    names.add(studyService.getSubjectVisitTableName(getContainer()));
                    names.add(VISIT_ALIASES);
                }

                if (SpecimenManager.get().isSpecimenModuleActive(getContainer()))
                {
                    names.add(SPECIMEN_EVENT_TABLE_NAME);
                    names.add(SPECIMEN_DETAIL_TABLE_NAME);
                    names.add(SPECIMEN_SUMMARY_TABLE_NAME);
                    names.add("SpecimenVialCount");
                    names.add(SIMPLE_SPECIMEN_TABLE_NAME);
                    names.add("SpecimenRequest");
                    names.add("SpecimenRequestStatus");
                    names.add("VialRequest");
                    names.add(SPECIMEN_ADDITIVE_TABLE_NAME);
                    names.add(SPECIMEN_DERIVATIVE_TABLE_NAME);
                    names.add(SPECIMEN_PRIMARY_TYPE_TABLE_NAME);
                    names.add("SpecimenComment");

                    // specimen report pivots
                    names.add(SpecimenPivotByPrimaryType.PIVOT_BY_PRIMARY_TYPE);
                    names.add(SpecimenPivotByDerivativeType.PIVOT_BY_DERIVATIVE_TYPE);
                    names.add(SpecimenPivotByRequestingLocation.PIVOT_BY_REQUESTING_LOCATION);

                    names.add(LOCATION_SPECIMEN_LIST_TABLE_NAME);
                }

                names.add(VISIT_MAP_TABLE_NAME);

                names.add("DataSets");
                names.add(DatasetColumnsTable.NAME);

                // Only show cohorts if the user has permission
                if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                    names.add("Cohort");

                // Subject category/group tables:
                names.add(studyService.getSubjectCategoryTableName(getContainer()));
                names.add(studyService.getSubjectGroupTableName(getContainer()));
                names.add(studyService.getSubjectGroupMapTableName(getContainer()));
                names.add(PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME);

                // Add only datasets that the user can read
                User user = getUser();
                for (DatasetDefinition dsd : _study.getDatasets())
                {
                    boolean canRead = dsd.canRead(user);
                    if (dsd.getName() == null || !canRead)
                        continue;
                    names.add(dsd.getName());
                }

                // study design tables
                names.add(PRODUCT_TABLE_NAME);
                names.add(PRODUCT_ANTIGEN_TABLE_NAME);
                names.add(TREATMENT_PRODUCT_MAP_TABLE_NAME);
                names.add(TREATMENT_TABLE_NAME);
                names.add(TREATMENT_VISIT_MAP_TABLE_NAME);
                names.add(OBJECTIVE_TABLE_NAME);
                names.add(PERSONNEL_TABLE_NAME);
                names.add(VISIT_TAG_TABLE_NAME);
                names.add(VISIT_TAG_MAP_TABLE_NAME);
                names.add(ASSAY_SPECIMEN_TABLE_NAME);
                names.add(ASSAY_SPECIMEN_VISIT_TABLE_NAME);

                names.add(STUDY_SNAPSHOT_TABLE_NAME);
            }
            _tableNames = Collections.unmodifiableSet(names);
        }

        return _tableNames;
    }

    public Map<String, DatasetDefinition> getDatasetDefinitions()
    {
        Map<String, DatasetDefinition> ret = new LinkedHashMap<>();
        if (null == _study)
            throw new IllegalStateException("Attempt to get datasets without a study");

        for (DatasetDefinition dsd : _study.getDatasets())
        {
            if (dsd.getName() == null)
                continue;
            ret.put(dsd.getName(), dsd);
        }
        return ret;
    }

    @Nullable
    public DatasetDefinition getDatasetDefinitionByName(String name)
    {
        if (null == _study)
            throw new IllegalStateException("Attempt to get datasets without a study");

        for (DatasetDefinition dsd : _study.getDatasets())
        {
            if (name.equalsIgnoreCase(dsd.getName()))
                return dsd;
        }
        return null;
    }

    /*
     * CONSIDER: use Schema.getTable() instead, use this only if you intend to manipulate the tableinfo in some way
     * UserSchema will call afterConstruct() for tables constructed the usual way
     */
    public DatasetTableImpl createDatasetTableInternal(DatasetDefinition definition, ContainerFilter cf)
    {
        try
        {
            DatasetTableImpl ret = DatasetFactory.createDataset(this, cf, definition);
            ret.afterConstruct();
            return ret;
        }
        catch (UnauthorizedException e)
        {
            return null;
        }
    }

    synchronized List<BigDecimal> getSequenceNumsForDataset(Dataset dsd)
    {
        if (null == _datasetSequenceMap)
            _datasetSequenceMap =  StudyManager.getInstance().getVisitManager(_study).getDatasetSequenceNums();

        return _datasetSequenceMap.get(dsd.getDatasetId());
    }


    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        StudyService studyService = StudyService.get();
        if (null == studyService)
            throw new IllegalStateException("No study service!");

        if (PROPERTIES_TABLE_NAME.equalsIgnoreCase(name) || STUDY_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyPropertiesTable(this, cf);
        }

        // Expose the simplified specimen table even if there's no study in this container. The caller may
        // want to set a container filter to look at specimens across the entire site
        if (SIMPLE_SPECIMEN_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            if (null != _study && _study.isAncillaryStudy() && null != _study.getSourceStudy())
            {
                Set<Container> containers = new HashSet<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                Map<Container, SQLFragment> filterFragments = getAncillaryStudyFilterFragments(sourceStudyContainer);
                // TODO ContainerFilter
                return studyService.getSpecimenTableUnion(this, containers, filterFragments, _dontAliasColumns, false);
            }
            return new SimpleSpecimenTable(this, cf, !_mustCheckPermissions);
        }

        if (VIAL_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            return new VialTable(this, cf);
        }

        if ("Site".equalsIgnoreCase(name) || LOCATION_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            if (null != _study && _study.isAncillaryStudy() && null != _study.getSourceStudy())
            {
                Set<Container> containers = new HashSet<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                // TODO ContainerFilter
                return studyService.getTypeTableUnion(LocationTable.class, this, containers, _dontAliasColumns);
            }
            return new LocationTable(this, cf);
        }

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

        if (STUDY_DATA_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDataTable(this, cf);
        }
        if ("Cohort".equalsIgnoreCase(name))
        {
            return new CohortTable(this, cf);
        }
        if (studyService.getSubjectTableName(getContainer()).equalsIgnoreCase(name))
        {
            return new ParticipantTable(this, cf, false);
        }
        if (studyService.getSubjectCategoryTableName(getContainer()).equalsIgnoreCase(name))
        {
            return new ParticipantCategoryTable(this, cf);
        }
        if (studyService.getSubjectGroupTableName(getContainer()).equalsIgnoreCase(name))
        {
            return new ParticipantGroupTable(this, cf);
        }
        if (studyService.getSubjectGroupMapTableName(getContainer()).equalsIgnoreCase(name))
        {
            return new ParticipantGroupMapTable(this, cf);
        }
        if (PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new ParticipantGroupCohortUnionTable(this, cf);
        }
        if (SPECIMEN_SUMMARY_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            if (_study.isAncillaryStudy() && null != _study.getSourceStudy())
            {
                Set<Container> containers = new HashSet<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                // TODO ContainerFilter
                Map<Container, SQLFragment> filterFragments = getAncillaryStudyFilterFragments(sourceStudyContainer);
                return studyService.getSpecimenSummaryTableUnion(this, containers, filterFragments, _dontAliasColumns, false);
            }
            return new SpecimenSummaryTable(this, cf);
        }
        if (SPECIMEN_DETAIL_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            if (_study.isAncillaryStudy() && null != _study.getSourceStudy())
            {
                Set<Container> containers = new HashSet<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                // TODO ContainerFilter
                Map<Container, SQLFragment> filterFragments = getAncillaryStudyFilterFragments(sourceStudyContainer);
                return studyService.getSpecimenDetailTableUnion(this, containers, filterFragments, _dontAliasColumns, false);
            }
            return new SpecimenDetailTable(this, cf);
        }
        if (SPECIMEN_WRAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            if (_study.isAncillaryStudy() && null != _study.getSourceStudy())
            {
                Set<Container> containers = new HashSet<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                // TODO ContainerFilter
                Map<Container, SQLFragment> filterFragments = getAncillaryStudyFilterFragments(sourceStudyContainer);
                return studyService.getSpecimenWrapTableUnion(this, containers, filterFragments, _dontAliasColumns, false);
            }
            return new SpecimenWrapTable(this, cf);
        }
        if ("SpecimenVialCount".equalsIgnoreCase(name))
        {
            return new SpecimenVialCountTable(this, cf);
        }
        if (SPECIMEN_EVENT_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            return new SpecimenEventTable(this, cf);
        }
        if ((studyService.getSubjectVisitTableName(getContainer()).equalsIgnoreCase(name) || "ParticipantVisit".equalsIgnoreCase(name)) && _study.getTimepointType() != TimepointType.CONTINUOUS)
        {
            return new ParticipantVisitTable(this, cf, false);
        }
        if ("SpecimenRequest".equalsIgnoreCase(name))
        {
            return new SpecimenRequestTable(this, cf);
        }
        if ("SpecimenRequestStatus".equalsIgnoreCase(name))
        {
            return new SpecimenRequestStatusTable(this, cf);
        }
        if (VISIT_TABLE_NAME.equalsIgnoreCase(name) && _study.getTimepointType() != TimepointType.CONTINUOUS)
        {
            return new VisitTable(this, cf);
        }
        if (VISIT_ALIASES.equalsIgnoreCase(name) && _study.getTimepointType() != TimepointType.CONTINUOUS)
        {
            return new VisitAliasesTable(this, cf);
        }
        if ("DataSets".equalsIgnoreCase(name))
        {
            return new DatasetsTable(this, cf);
        }
        if (DatasetColumnsTable.NAME.equalsIgnoreCase(name))
        {
            return new DatasetColumnsTable(this, cf);
        }
        if (QCSTATE_TABLE_NAME.equalsIgnoreCase(name))
        {
            // Moved to core but kept here for backwards compatibility
            return new QCStateTable(this, cf);
        }
        if (SPECIMEN_ADDITIVE_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            if (_study.isAncillaryStudy() && null != _study.getSourceStudy())
            {
                Set<Container> containers = new HashSet<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                // TODO ContainerFilter
                return studyService.getTypeTableUnion(AdditiveTypeTable.class, this, containers, _dontAliasColumns);
            }
            return new AdditiveTypeTable(this, cf);
        }
        if (SPECIMEN_DERIVATIVE_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            if (_study.isAncillaryStudy() && null != _study.getSourceStudy())
            {
                Set<Container> containers = new HashSet<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                // TODO ContainerFilter
                return studyService.getTypeTableUnion(DerivativeTypeTable.class, this, containers, _dontAliasColumns);
            }
            return new DerivativeTypeTable(this, cf);
        }
        if (SPECIMEN_PRIMARY_TYPE_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (getContainer().isRoot())
                return null;
            if (_study.isAncillaryStudy() && null != _study.getSourceStudy())
            {
                Set<Container> containers = new HashSet<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                // TODO ContainerFilter
                return studyService.getTypeTableUnion(PrimaryTypeTable.class, this, containers, _dontAliasColumns);
            }
            return new PrimaryTypeTable(this, cf);
        }
        if ("SpecimenComment".equalsIgnoreCase(name))
        {
            return new SpecimenCommentTable(this, cf);
        }
        if ("VialRequest".equalsIgnoreCase(name))
        {
            return new VialRequestTable(this, cf);
        }
        if (STUDY_SNAPSHOT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudySnapshotTable(this, cf);
        }
        if (PRODUCT_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyProductDomainKind domainKind = new StudyProductDomainKind();
            Domain domain = domainKind.getDomain(getContainer(), PRODUCT_TABLE_NAME);

            return StudyProductTable.create(domain, this, isDataspaceProject() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (PRODUCT_ANTIGEN_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyProductAntigenDomainKind domainKind = new StudyProductAntigenDomainKind();
            Domain domain = domainKind.getDomain(getContainer(), PRODUCT_ANTIGEN_TABLE_NAME);

            return StudyProductAntigenTable.create(domain, this, isDataspaceProject() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (TREATMENT_PRODUCT_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyTreatmentProductDomainKind domainKind = new StudyTreatmentProductDomainKind();
            Domain domain = domainKind.getDomain(getContainer(), TREATMENT_PRODUCT_MAP_TABLE_NAME);

            return StudyTreatmentProductTable.create(domain, this, isDataspace() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (TREATMENT_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyTreatmentDomainKind domainKind = new StudyTreatmentDomainKind();
            Domain domain = domainKind.getDomain(getContainer(), TREATMENT_TABLE_NAME);

            return StudyTreatmentTable.create(domain, this, isDataspace() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (TREATMENT_VISIT_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyTreatmentVisitMapTable(this, cf);
        }
        if (SpecimenPivotByPrimaryType.PIVOT_BY_PRIMARY_TYPE.equalsIgnoreCase(name))
        {
            return new SpecimenPivotByPrimaryType(SpecimenQuerySchema.get(getStudy(), getUser()), cf);
        }
        if (SpecimenPivotByDerivativeType.PIVOT_BY_DERIVATIVE_TYPE.equalsIgnoreCase(name))
        {
            return new SpecimenPivotByDerivativeType(SpecimenQuerySchema.get(getStudy(), getUser()), cf);
        }
        if (SpecimenPivotByRequestingLocation.PIVOT_BY_REQUESTING_LOCATION.equalsIgnoreCase(name))
        {
            return new SpecimenPivotByRequestingLocation(SpecimenQuerySchema.get(getStudy(), getUser()), cf);
        }
        if (LOCATION_SPECIMEN_LIST_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new LocationSpecimenListTable(this, cf);
        }
        if (PERSONNEL_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyPersonnelDomainKind domainKind = new StudyPersonnelDomainKind();
            Domain domain = domainKind.getDomain(getContainer(), PERSONNEL_TABLE_NAME);

            // TODO ContainerFilter
            return StudyPersonnelTable.create(domain, this, isDataspaceProject() ? ContainerFilter.Type.Project.create(this) : null);
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
        if (VISIT_TAG_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new VisitTagTable(this, isDataspaceProject() ? ContainerFilter.Type.Project.create(this) : cf);
        }
        if (VISIT_TAG_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new VisitTagMapTable(this, cf);
        }
        if (VISIT_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new VisitMapTable(this, cf);
        }
        if (name.startsWith(VISUALIZATION_VISIT_TAG_TABLE_NAME))
        {
            // Name is encoded with useProtocolDay boolean, tagName, and altQueryName
            String params = name.substring(VISUALIZATION_VISIT_TAG_TABLE_NAME.length());
            boolean useProtocolDay;
            if (params.startsWith("-true"))
            {
                params = params.substring(params.indexOf("-true") + 6);
                useProtocolDay = true;
            }
            else
            {
                params = params.substring(params.indexOf("-false") + 7);
                useProtocolDay = false;
            }
            int hyphenIndex = params.indexOf("-");
            String tagName = hyphenIndex > -1 ? params.substring(0, hyphenIndex) : params;
            String altQueryName = hyphenIndex > -1 ? params.substring(hyphenIndex + 1) : null;

            return new VisualizationVisitTagTable(this, cf, getStudy(), getUser(), tagName, useProtocolDay, altQueryName);
        }

        // Might be a dataset
        DatasetDefinition dsd = getDatasetDefinitionByQueryName(name);

        if (null != dsd)
        {
            try
            {
                return DatasetFactory.createDataset(this, cf, dsd);
            }
            catch (UnauthorizedException e)
            {
                return null;
            }
        }
        
        return null;
    }

    @Override
    public TableInfo getUnionTable(TableInfo tableInfo, Set<Container> containers)
    {
        StudyService studyService = StudyService.get();
        assert null != studyService;

        if (1 == containers.size() && getContainer() == containers.toArray()[0])
            return tableInfo;   // No need to union 1 table

        if (SpecimenTable.class == tableInfo.getClass())
            return studyService.getSpecimenTableUnion(this, containers, new HashMap<>(), _dontAliasColumns, false);
        if (VialTable.class == tableInfo.getClass())
            return studyService.getVialTableUnion(this, containers);
        if (SpecimenDetailTable.class == tableInfo.getClass())
            return studyService.getSpecimenDetailTableUnion(this, containers, new HashMap<>(), _dontAliasColumns, false);
        if (SpecimenWrapTable.class == tableInfo.getClass())
            return studyService.getSpecimenWrapTableUnion(this, containers, new HashMap<>(), _dontAliasColumns, false);
        if (SpecimenSummaryTable.class == tableInfo.getClass())
            return studyService.getSpecimenSummaryTableUnion(this, containers, new HashMap<>(), _dontAliasColumns, false);
        if (LocationTable.class == tableInfo.getClass())
            return studyService.getTypeTableUnion(LocationTable.class, this, containers, _dontAliasColumns);
        if (PrimaryTypeTable.class == tableInfo.getClass())
            return studyService.getTypeTableUnion(PrimaryTypeTable.class, this, containers, _dontAliasColumns);
        if (DerivativeTypeTable.class == tableInfo.getClass())
            return studyService.getTypeTableUnion(DerivativeTypeTable.class, this, containers, _dontAliasColumns);
        if (AdditiveTypeTable.class == tableInfo.getClass())
            return studyService.getTypeTableUnion(AdditiveTypeTable.class, this, containers, _dontAliasColumns);

        throw new IllegalStateException("Table '" + tableInfo.getName() + "' does not have a Union table.");
    }

    // Simple helper to keep us consistent... try getting by name first, then by label
    private @Nullable
    DatasetDefinition getDatasetDefinitionByQueryName(String queryName)
    {
        assert _study != null : "Attempt to get datasets without a study";
        if (null == queryName)
            return null;

        return StudyManager.getInstance().getDatasetDefinitionByQueryName(_study, queryName);
    }

    private Map<Container, SQLFragment> getAncillaryStudyFilterFragments(Container sourceStudyContainer)
    {
        assert _study != null && _study.isAncillaryStudy();       // Don't call if it's not
        Map<Container, SQLFragment> filterFragments = new HashMap<>();
        List<String> ptids = ParticipantGroupManager.getInstance().getAllGroupedParticipants(_study.getContainer());
        if (!ptids.isEmpty())
        {
            SQLFragment condition = new SQLFragment("(PTID ");
            getDbSchema().getSqlDialect().appendInClauseSql(condition, ptids);
            condition.append(") ");
            filterFragments.put(sourceStudyContainer, condition);
        }
        return filterFragments;
    }

    // Null if there's no alias dataset configured, otherwise a "skinny" table with just Participant, Source, Alias, and Modified
    // in that order but having the configured names.
    @Nullable public TableInfo getParticipantAliasesTable()
    {
        StudyImpl study = getStudy();

        if (null != study)
        {
            Integer id = study.getParticipantAliasDatasetId();

            if (null != id)
            {
                DatasetDefinition def = study.getDataset(id);

                if (null != def)
                {
                    DatasetTableImpl datasetTable = DatasetFactory.createDataset(this, null, def);

                    String aliasName = study.getParticipantAliasProperty();
                    String sourceName = study.getParticipantAliasSourceProperty();

                    ColumnInfo aliasColumn = datasetTable.getColumn(aliasName);
                    ColumnInfo sourceColumn = datasetTable.getColumn(sourceName);

                    if (null != aliasColumn && null != sourceColumn)
                    {
                        FilteredTable aliasTable = new FilteredTable<>(datasetTable, this);

                        // Note: Keep these columns in order... they are selected by ordinal since their column names vary
                        aliasTable.addWrapColumn(datasetTable.getColumn(study.getSubjectColumnName()));
                        aliasTable.addWrapColumn(aliasColumn);
                        aliasTable.addWrapColumn(sourceColumn);
                        aliasTable.addWrapColumn(datasetTable.getColumn("Modified"));

                        return aliasTable;
                    }
                }
            }
        }

        return null;
    }

    @Override
    protected QuerySettings createQuerySettings(String dataRegionName, String queryName, String viewName)
    {
        if (DatasetQueryView.DATAREGION.equals(dataRegionName))
            return new DatasetQuerySettings(dataRegionName);

        return super.createQuerySettings(dataRegionName, queryName, viewName);
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        if (getStudy() != null)
        {
            if ("SpecimenDetail".equalsIgnoreCase(settings.getQueryName()))
            {
                return SpecimenQueryView.createView(context, settings, SpecimenQueryView.ViewType.VIALS);
            }

            if ("Cohort".equalsIgnoreCase(settings.getQueryName()))
            {
                return new CohortQueryView(context.getUser(), getStudy(), context);
            }

            if ("Location".equalsIgnoreCase(settings.getQueryName()))
            {
                return new LocationQueryView(this, settings, errors);
            }

            DatasetDefinition dsd = getDatasetDefinitionByQueryName(settings.getQueryName());
            // Check for permission before deciding to treat the request as a dataset
            if (dsd != null && dsd.canRead(getUser()))
            {
                // Issue 18787: if dataset name and label differ, use the name for the queryName
                if (!settings.getQueryName().equals(dsd.getName()))
                    settings.setQueryName(dsd.getName());

                if (!(settings instanceof DatasetQuerySettings))
                    settings = new DatasetQuerySettings(settings);

                return new DatasetQueryView(this, (DatasetQuerySettings)settings, errors);
            }
        }
        return super.createView(context, settings, errors);
    }

    @Nullable
    public StudyImpl getStudy()
    {
        return _study;
    }

    public String decideTableName(DatasetDefinition dsd)
    {
        return dsd.getName();
    }

    public String decideTableName(VisitImpl visit)
    {
        return visit.getLabel();
    }

    public boolean getMustCheckPermissions()
    {
        return _mustCheckPermissions;
    }

    @Override
    @Nullable
    public String getDomainURI(String queryName)
    {
        if (CohortImpl.DOMAIN_INFO.getDomainName().equals(queryName))
            return CohortImpl.DOMAIN_INFO.getDomainURI(getContainer());
        if (StudyPropertiesQueryView.QUERY_NAME.equals(queryName))
            return StudyImpl.DOMAIN_INFO.getDomainURI(getContainer());

        Study study = StudyManager.getInstance().getStudy(getContainer());
        if (study == null)
            return null;
        
        DatasetDefinition def = getDatasetDefinitionByQueryName(queryName);
        if (def != null)
        {
            if (def.canRead(getUser()))
                return def.getTypeURI();
            else
                throw new RuntimeException("User does not have permission to read that dataset");
        }

        return null;
    }

    @Nullable
    @Override
    public VisualizationProvider createVisualizationProvider()
    {
        return new StudyVisualizationProvider(this);
    }

    @Override
    public NavTree getSchemaBrowserLinks(User user)
    {
        NavTree root = super.getSchemaBrowserLinks(user);
        if (getContainer().hasPermission(user, AdminPermission.class))
            root.addChild("Manage datasets", new ActionURL(StudyController.ManageTypesAction.class, getContainer()));
        return root;
    }

    @Override
    public boolean isHidden()
    {
        // Don't display the study module isn't active in the container
        Module studyModule = ModuleLoader.getInstance().getModule(StudyModule.MODULE_NAME);
        return !getContainer().getActiveModules().contains(studyModule);
    }

    public boolean getDontAliasColumns()
    {
        return _dontAliasColumns;
    }

    public void setDontAliasColumns(boolean dontAliasColumns)
    {
        _dontAliasColumns = dontAliasColumns;
    }


    /** for tables that support container filter, should they turn on support or not */
    public boolean allowSetContainerFilter()
    {
        return true;
    }

    /** for tables that support container filter, the default container filter in this study */
    @Override
    public ContainerFilter getDefaultContainerFilter()
    {
        return ContainerFilter.current(getContainer());
    }

    protected void initSessionParticipantGroup(StudyImpl study, User user)
    {
        if (study == null)
            return;

        ViewContext context = HttpView.hasCurrentView() ? HttpView.currentContext() : null;
        if (context == null)
            return;

        // The session sticky participant filter only applies to the current container -- not across any child shared studies
        ParticipantGroup group = ParticipantGroupManager.getInstance().getSessionParticipantGroup(study.getContainer(), user, context.getRequest());
        if (group != null)
            setSessionParticipantGroup(group);
    }

    /** for tables that have a participant, apply a session based "sticky" participant group filter. */
    public void setSessionParticipantGroup(ParticipantGroup sessionParticipantGroup)
    {
        _sessionParticipantGroup = sessionParticipantGroup;
    }

    public ParticipantGroup getSessionParticipantGroup()
    {
        return _sessionParticipantGroup;
    }

    public boolean isDataspace()
    {
        return false;
    }

    @Override
    protected void populateQueryNameToLabelMap(Map<String, QueryDefinition> queries, TreeMap<String, String> namesAndLabels)
    {
        for (QueryDefinition queryDefinition : queries.values())
        {
            String name = queryDefinition.getName();
            String label = queryDefinition.getTitle();
            namesAndLabels.put(name, label);
        }
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

    public class TablePackage
    {
        private TableInfo _tableInfo;
        private Container _container;
        private boolean _isProjectLevel;

        public TablePackage(TableInfo tableInfo, Container container, boolean isProjectLevel)
        {
            _tableInfo = tableInfo;
            _container = container;
            _isProjectLevel = isProjectLevel;
        }

        public TableInfo getTableInfo()
        {
            return _tableInfo;
        }

        public Container getContainer()
        {
            return _container;
        }

        public boolean isProjectLevel()
        {
            return _isProjectLevel;
        }
    }

    // Called on a study folder; return project-level table and container if appropriate; otherwise table from this container
    public TablePackage getTablePackage(AbstractContext ctx, StudyQuerySchema projectSchema, String tableName, ContainerFilter cf)
    {
        TableInfo tableInfo;
        Container container;
        boolean isProjectLevel = false;
        if (ctx.isDataspaceProject() && StudyQuerySchema.isDataspaceProjectTable(tableName))
        {
            tableInfo = projectSchema.getTable(tableName, cf, true, true);
            container = ctx.getProject();
            isProjectLevel = true;
        }
        else
        {
            tableInfo = this.getTable(tableName, cf, true, true);
            container = ctx.getContainer();
        }
        return new TablePackage(tableInfo, container, isProjectLevel);
    }

    private static final Set<String> _dataspaceProjectLevelTables = new HashSet<>();
    private static final Set<String> _dataspaceFolderLevelTables = new HashSet<>();
    static
    {
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_CHALLENGE_TYPES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_GENES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_ROUTES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_SUB_TYPES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_ASSAYS_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_LABS_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_UNITS_TABLE_NAME);
        _dataspaceProjectLevelTables.add(PRODUCT_TABLE_NAME);
        _dataspaceProjectLevelTables.add(PRODUCT_ANTIGEN_TABLE_NAME);
        _dataspaceProjectLevelTables.add(DOSE_AND_ROUTE_TABLE_NAME);
        _dataspaceProjectLevelTables.add(PERSONNEL_TABLE_NAME);
        _dataspaceProjectLevelTables.add(VISIT_TAG_TABLE_NAME);
        _dataspaceProjectLevelTables.add(VISIT_TABLE_NAME);

        _dataspaceFolderLevelTables.add(TREATMENT_TABLE_NAME);
        _dataspaceFolderLevelTables.add(ASSAY_SPECIMEN_TABLE_NAME);
        _dataspaceFolderLevelTables.add(OBJECTIVE_TABLE_NAME);
    }

    public static boolean isDataspaceProjectTable(String tableName)
    {
        return _dataspaceProjectLevelTables.contains(tableName);
    }

    public static boolean isDataspaceFolderTable(String tableName)
    {
        return _dataspaceFolderLevelTables.contains(tableName);
    }



    private class DatasetSchema extends StudyQuerySchema
    {
        final StudyQuerySchema _parentSchema;

        DatasetSchema(StudyQuerySchema parent)
        {
            super(new SchemaKey(parent.getSchemaPath(), "Datasets"), "Contains all collected data related to the study (except specimens), including subjects, cohort assignments, datasets, etc.", parent.getStudy(), parent.getContainer(), parent.getUser(), parent._mustCheckPermissions);
            _parentSchema = parent;
            setSessionParticipantGroup(parent.getSessionParticipantGroup());
        }

        @Override
        public Set<String> getSubSchemaNames()
        {
            return Collections.emptySet();
        }

        @Override
        public QuerySchema getSchema(String name)
        {
            return _parentSchema.getSchema(name);
        }

        @Override
        public Set<String> getTableNames()
        {
            if (null == _tableNames)
            {
                Set<String> names = new LinkedHashSet<>();
                if (_study != null)
                {
                    StudyService studyService = StudyService.get();
                    if (null == studyService)
                        throw new IllegalStateException("No StudyService!");

                    names.add(STUDY_DATA_TABLE_NAME);
                    names.add(studyService.getSubjectTableName(getContainer()));

                    names.add(studyService.getSubjectGroupMapTableName(getContainer()));

                    User user = getUser();
                    for (DatasetDefinition dsd : _study.getDatasets())
                    {
                        boolean canRead = dsd.canRead(user);
                        if (dsd.getName() == null || !canRead)
                            continue;
                        names.add(dsd.getName());
                    }
                }

                _tableNames = Collections.unmodifiableSet(names);
            }
            return _tableNames;
        }
    }


    private class DesignSchema extends StudyQuerySchema
    {
        final StudyQuerySchema _parentSchema;

        DesignSchema(StudyQuerySchema parent)
        {
            super(new SchemaKey(parent.getSchemaPath(), "Design"), "Contains all study design", parent.getStudy(), parent.getContainer(), parent.getUser(), parent._mustCheckPermissions);
            _parentSchema = parent;
            setSessionParticipantGroup(parent.getSessionParticipantGroup());
        }

        @Override
        public Set<String> getSubSchemaNames()
        {
            return Collections.emptySet();
        }

        @Override
        public QuerySchema getSchema(String name)
        {
            return _parentSchema.getSchema(name);
        }

        @Override
        public Set<String> getTableNames()
        {
            if (_tableNames == null)
            {
                Set<String> names = new LinkedHashSet<>();

                // Always add StudyProperties and study designer lookup tables, even if we have no study
                names.add(PROPERTIES_TABLE_NAME);
                names.add(STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME);
                names.add(STUDY_DESIGN_CHALLENGE_TYPES_TABLE_NAME);
                names.add(STUDY_DESIGN_GENES_TABLE_NAME);
                names.add(STUDY_DESIGN_ROUTES_TABLE_NAME);
                names.add(STUDY_DESIGN_SUB_TYPES_TABLE_NAME);
                names.add(STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME);
                names.add(STUDY_DESIGN_UNITS_TABLE_NAME);
                names.add(STUDY_DESIGN_ASSAYS_TABLE_NAME);
                names.add(STUDY_DESIGN_LABS_TABLE_NAME);
                names.add(DOSE_AND_ROUTE_TABLE_NAME);

                if (_study != null)
                {
                    StudyService studyService = StudyService.get();
                    if (null == studyService)
                        throw new IllegalStateException("No StudyService!");

                    // All these require studies defined
                    if (_study.getTimepointType() != TimepointType.CONTINUOUS)
                        names.add(VISIT_TABLE_NAME);

                    if (_study.getTimepointType() != TimepointType.CONTINUOUS)
                        names.add(studyService.getSubjectVisitTableName(getContainer()));

                    names.add("DataSets");
                    names.add(DatasetColumnsTable.NAME);

                    // Only show cohorts if the user has permission
                    if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                        names.add("Cohort");

                    names.add(QCSTATE_TABLE_NAME);

                    // Subject category/group tables:
                    names.add(studyService.getSubjectCategoryTableName(getContainer()));
                    names.add(studyService.getSubjectGroupTableName(getContainer()));
                    names.add(PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME);

                    // assay schedule tables
                    names.add(ASSAY_SPECIMEN_TABLE_NAME);
                    names.add(ASSAY_SPECIMEN_VISIT_TABLE_NAME);

                    // study designs
                    names.add(PRODUCT_TABLE_NAME);
                    names.add(PRODUCT_ANTIGEN_TABLE_NAME);
                    names.add(TREATMENT_PRODUCT_MAP_TABLE_NAME);
                    names.add(TREATMENT_TABLE_NAME);
                    names.add(TREATMENT_VISIT_MAP_TABLE_NAME);

                    names.add(OBJECTIVE_TABLE_NAME);
                    names.add(PERSONNEL_TABLE_NAME);
                    names.add(VISIT_TAG_TABLE_NAME);
                    names.add(VISIT_TAG_MAP_TABLE_NAME);
                    names.add(STUDY_SNAPSHOT_TABLE_NAME);
                }
                _tableNames = Collections.unmodifiableSet(names);            }

            return _tableNames;
        }
    }

    @Override
    public boolean hasRegisteredSchemaLinks()
    {
        return true;
    }
}
