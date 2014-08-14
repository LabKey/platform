/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.studydesign.StudyDesignAssaysTable;
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
import org.labkey.study.writer.AbstractContext;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collection;
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
    public static final String SCHEMA_NAME = "study";
    public static final String SCHEMA_DESCRIPTION = "Contains all data related to the study, including subjects, cohorts, visits, datasets, specimens, etc.";
    public static final String SIMPLE_SPECIMEN_TABLE_NAME = "SimpleSpecimen";
    public static final String SPECIMEN_DETAIL_TABLE_NAME = "SpecimenDetail";
    public static final String SPECIMEN_WRAP_TABLE_NAME = "SpecimenWrap";
    public static final String SPECIMEN_EVENT_TABLE_NAME = "SpecimenEvent";
    public static final String SPECIMEN_SUMMARY_TABLE_NAME = "SpecimenSummary";
    public static final String PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME = "ParticipantGroupCohortUnion";
    public static final String LOCATION_SPECIMEN_LIST_TABLE_NAME = "LocationSpecimenList";

    public static final String STUDY_TABLE_NAME = "Study";
    public static final String PROPERTIES_TABLE_NAME = "StudyProperties";
    public static final String STUDY_SNAPSHOT_TABLE_NAME = "StudySnapshot";
    public static final String OBJECTIVE_TABLE_NAME = "Objective";
    public static final String PERSONNEL_TABLE_NAME = "Personnel";
    public static final String VISIT_TAG_TABLE_NAME = "VisitTag";
    public static final String VISIT_TAG_MAP_TABLE_NAME = "VisitTagMap";
    public static final String ASSAY_SPECIMEN_TABLE_NAME = "AssaySpecimen";
    public static final String ASSAY_SPECIMEN_VISIT_TABLE_NAME = "AssaySpecimenVisit";
    public static final String VISUALIZTION_VISIT_TAG_TABLE_NAME = "VisualizationVisitTag";

    // extensible study data tables
    public static final String STUDY_DESIGN_SCHEMA_NAME = "studydesign";
    public static final String PRODUCT_TABLE_NAME = "Product";
    public static final String PRODUCT_ANTIGEN_TABLE_NAME = "ProductAntigen";
    public static final String TREATMENT_TABLE_NAME = "Treatment";
    public static final String TREATMENT_PRODUCT_MAP_TABLE_NAME = "TreatmentProductMap";
    public static final String TREATMENT_VISIT_MAP_TABLE_NAME = "TreatmentVisitMap";

    // study design tables
    public static final String STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME = "StudyDesignImmunogenTypes";
    public static final String STUDY_DESIGN_GENES_TABLE_NAME = "StudyDesignGenes";
    public static final String STUDY_DESIGN_ROUTES_TABLE_NAME = "StudyDesignRoutes";
    public static final String STUDY_DESIGN_SUB_TYPES_TABLE_NAME = "StudyDesignSubTypes";
    public static final String STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME = "StudyDesignSampleTypes";
    public static final String STUDY_DESIGN_UNITS_TABLE_NAME = "StudyDesignUnits";
    public static final String STUDY_DESIGN_ASSAYS_TABLE_NAME = "StudyDesignAssays";
    public static final String STUDY_DESIGN_LABS_TABLE_NAME = "StudyDesignLabs";

    @Nullable // if no study defined in this container
    final StudyImpl _study;

    final boolean _mustCheckPermissions;
    private boolean _dontAliasColumns = false;

    private Map<Integer, List<Double>> _datasetSequenceMap;
    public static final String STUDY_DATA_TABLE_NAME = "StudyData";
    public static final String QCSTATE_TABLE_NAME = "QCState";

    public StudyQuerySchema(StudyImpl study, User user, boolean mustCheckPermissions)
    {
        super(SCHEMA_NAME, SCHEMA_DESCRIPTION, user, study.getContainer(), StudySchema.getInstance().getSchema());
        _study = study;
        _mustCheckPermissions = mustCheckPermissions;
    }

    /**
     * This c-tor is for schemas that have no study defined -- _study is null!
     */
    private StudyQuerySchema(Container c, User user)
    {
        super(SCHEMA_NAME, SCHEMA_DESCRIPTION, user, c, StudySchema.getInstance().getSchema());
        _study = null;
        _mustCheckPermissions = true;
    }


    public static StudyQuerySchema createSchema(StudyImpl study, User user, boolean mustCheckPermissions)
    {
        if (study.isDataspaceStudy())
            return new DataspaceQuerySchema(study, user, mustCheckPermissions);
        else
            return new StudyQuerySchema(study, user, mustCheckPermissions);
    }


    static StudyQuerySchema createSchemaWithoutStudy(Container c, User u)
    {
        return new StudyQuerySchema(c, u);
    }


    private DbSchema getStudyDesignSchema()
    {
        return DbSchema.get(STUDY_DESIGN_SCHEMA_NAME, DbSchemaType.Provisioned);
    }

    public String getSubjectColumnName()
    {
        if (null != _study)
            return _study.getSubjectColumnName();
        return "ParticipantId";
    }


    // TODO remove this when UserSchema implements caching
    // see https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=14369
    // we're not quite ready for general table caching, so just cache BaseSpecimenPivotTable
    Map<Pair<String,Boolean>,Object> pivotCache = new HashMap<>();

    @Override
    public Object _getTableOrQuery(String name, boolean includeExtraMetadata, boolean forWrite, Collection<QueryException> errors)
    {
        Pair<String, Boolean> key = new Pair<>(name.toLowerCase(),includeExtraMetadata);
        Object torq = forWrite ? pivotCache.get(key) : null;
        if (null != torq)
            return torq;
        Object o = super._getTableOrQuery(name, includeExtraMetadata, forWrite, errors);
        if (o instanceof BaseSpecimenPivotTable && errors.isEmpty() && !forWrite)
        {
            ((TableInfo)o).setLocked(true);
            pivotCache.put(key, o);
        }
        return o;
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
    public Set<String> getVisibleTableNames()
    {
        return getTableNames(true);
    }

    @Override
    public Set<String> getTableNames()
    {
        return getTableNames(false);
    }

    private Set<String> getTableNames(boolean visible)
    {
        Set<String> ret = new LinkedHashSet<>();

        // Always add StudyProperties and study designer lookup tables, even if we have no study
        ret.add(PROPERTIES_TABLE_NAME);
        ret.add(STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME);
        ret.add(STUDY_DESIGN_GENES_TABLE_NAME);
        ret.add(STUDY_DESIGN_ROUTES_TABLE_NAME);
        ret.add(STUDY_DESIGN_SUB_TYPES_TABLE_NAME);
        ret.add(STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME);
        ret.add(STUDY_DESIGN_UNITS_TABLE_NAME);
        ret.add(STUDY_DESIGN_ASSAYS_TABLE_NAME);
        ret.add(STUDY_DESIGN_LABS_TABLE_NAME);

        if (_study != null)
        {
            // All these require studies defined
            ret.add(STUDY_DATA_TABLE_NAME);
            ret.add(StudyService.get().getSubjectTableName(getContainer()));
            ret.add("Location");
            if (_study.getTimepointType() != TimepointType.CONTINUOUS)
                ret.add("Visit");

            if (_study.getTimepointType() != TimepointType.CONTINUOUS)
                ret.add(StudyService.get().getSubjectVisitTableName(getContainer()));

            ret.add(SPECIMEN_EVENT_TABLE_NAME);
            ret.add(SPECIMEN_DETAIL_TABLE_NAME);
            ret.add(SPECIMEN_SUMMARY_TABLE_NAME);
            ret.add("SpecimenVialCount");
            ret.add(SIMPLE_SPECIMEN_TABLE_NAME);
            ret.add("SpecimenRequest");
            ret.add("SpecimenRequestStatus");
            ret.add("VialRequest");
            ret.add("SpecimenAdditive");
            ret.add("SpecimenDerivative");
            ret.add("SpecimenPrimaryType");
            ret.add("SpecimenComment");

            ret.add("DataSets");
            ret.add(DataSetColumnsTable.NAME);

            // Only show cohorts if the user has permission
            if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                ret.add("Cohort");

            ret.add("QCState");

            // Subject category/group tables:
            ret.add(StudyService.get().getSubjectCategoryTableName(getContainer()));
            ret.add(StudyService.get().getSubjectGroupTableName(getContainer()));
            ret.add(StudyService.get().getSubjectGroupMapTableName(getContainer()));
            ret.add(PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME);

            // specimen report pivots
            ret.add(SpecimenPivotByPrimaryType.PIVOT_BY_PRIMARY_TYPE);
            ret.add(SpecimenPivotByDerivativeType.PIVOT_BY_DERIVATIVE_TYPE);
            ret.add(SpecimenPivotByRequestingLocation.PIVOT_BY_REQUESTING_LOCATION);

            ret.add(LOCATION_SPECIMEN_LIST_TABLE_NAME);

            // assay schedule tables
            ret.add(ASSAY_SPECIMEN_TABLE_NAME);
            ret.add(ASSAY_SPECIMEN_VISIT_TABLE_NAME);

            // Add only datasets that the user can read
            User user = getUser();
            for (DataSetDefinition dsd : _study.getDataSets())
            {
                boolean canRead = dsd.canRead(user);
                if (dsd.getName() == null || !canRead)
                    continue;
                ret.add(dsd.getName());
            }

            // study designs
            ret.add(PRODUCT_TABLE_NAME);
            ret.add(PRODUCT_ANTIGEN_TABLE_NAME);
            ret.add(TREATMENT_PRODUCT_MAP_TABLE_NAME);
            ret.add(TREATMENT_TABLE_NAME);
            ret.add(TREATMENT_VISIT_MAP_TABLE_NAME);

            ret.add(OBJECTIVE_TABLE_NAME);
            ret.add(PERSONNEL_TABLE_NAME);
            ret.add(VISIT_TAG_TABLE_NAME);
            ret.add(VISIT_TAG_MAP_TABLE_NAME);
            ret.add(STUDY_SNAPSHOT_TABLE_NAME);
        }
        return ret;
    }

    public Map<String, DataSetDefinition> getDataSetDefinitions()
    {
        Map<String, DataSetDefinition> ret = new LinkedHashMap<>();
        assert _study != null : "Attempt to get datasets without a study";
        if (_study != null)
        {
            for (DataSetDefinition dsd : _study.getDataSets())
            {
                if (dsd.getName() == null)
                    continue;
                ret.put(dsd.getName(), dsd);
            }
        }
        return ret;
    }

    @Nullable
    public DataSetDefinition getDataSetDefinitionByName(String name)
    {
        assert _study != null : "Attempt to get datasets without a study";
        if (_study != null)
        {
            for (DataSetDefinition dsd : _study.getDataSets())
            {
                if (name.equalsIgnoreCase(dsd.getName()))
                    return dsd;
            }
        }
        return null;
    }

    /*
     * CONSIDER: use Schema.getTable() instead, use this only if you intend to manipulate the tableinfo in some way
     * UserSchema will call afterConstruct() for tables constructed the usual way
     */
    public DataSetTableImpl createDatasetTableInternal(DataSetDefinition definition)
    {
        try
        {
            DataSetTableImpl ret = new DataSetTableImpl(this, definition);
            ret.afterConstruct();
            return ret;
        }
        catch (UnauthorizedException e)
        {
            return null;
        }
    }

    synchronized List<Double> getSequenceNumsForDataset(DataSet dsd)
    {
        if (null == _datasetSequenceMap)
            _datasetSequenceMap =  StudyManager.getInstance().getVisitManager(_study).getDatasetSequenceNums();

        return _datasetSequenceMap.get(dsd.getDataSetId());
    }


    @Override
    public TableInfo createTable(String name)
    {
        if (PROPERTIES_TABLE_NAME.equalsIgnoreCase(name) || STUDY_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyPropertiesTable ret = new StudyPropertiesTable(this);
            return ret;
        }

        // Expose the simplified specimen table even if there's no study in this container. The caller may
        // want to set a container filter to look at specimens across the entire site
        if (SIMPLE_SPECIMEN_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (_study.isAncillaryStudy())
            {
                List<Container> containers = new ArrayList<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                Map<Container, SQLFragment> filterFragments = getAncillaryStudyFilterFragments(sourceStudyContainer);
                return StudyService.get().getSpecimenTableUnion(this, containers, filterFragments, _dontAliasColumns, false);
            }
            return new SimpleSpecimenTable(this, !_mustCheckPermissions);
        }

        if ("Vial".equalsIgnoreCase(name))
        {
            return new VialTable(this);
        }

        if ("Site".equalsIgnoreCase(name) || "Location".equalsIgnoreCase(name))
        {
            LocationTable ret = new LocationTable(this);
            return ret;
        }

        // always expose the study designer lookup tables
        if (STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignImmunogenTypesTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (STUDY_DESIGN_GENES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignGenesTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (STUDY_DESIGN_ROUTES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignRoutesTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (STUDY_DESIGN_SUB_TYPES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignSubTypesTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignSampleTypesTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (STUDY_DESIGN_UNITS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignUnitsTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (STUDY_DESIGN_ASSAYS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignAssaysTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (STUDY_DESIGN_LABS_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyDesignLabsTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }

        if (_study == null)
            return null;

        if (STUDY_DATA_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyDataTable ret = new StudyDataTable(this);
            return ret;
        }
        if ("Cohort".equalsIgnoreCase(name))
        {
            CohortTable ret = new CohortTable(this);
            return ret;
        }
        if (StudyService.get().getSubjectTableName(getContainer()).equalsIgnoreCase(name))
        {
            ParticipantTable ret = new ParticipantTable(this, false);
            return ret;
        }
        if (StudyService.get().getSubjectCategoryTableName(getContainer()).equalsIgnoreCase(name))
        {
            ParticipantCategoryTable ret = new ParticipantCategoryTable(this);
            return ret;
        }
        if (StudyService.get().getSubjectGroupTableName(getContainer()).equalsIgnoreCase(name))
        {
            ParticipantGroupTable ret = new ParticipantGroupTable(this);
            return ret;
        }
        if (StudyService.get().getSubjectGroupMapTableName(getContainer()).equalsIgnoreCase(name))
        {
            ParticipantGroupMapTable ret = new ParticipantGroupMapTable(this); 
            return ret;
        }
        if (PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME.equalsIgnoreCase(name))
        {
            ParticipantGroupCohortUnionTable ret = new ParticipantGroupCohortUnionTable(this);
            return ret;
        }
        if (SPECIMEN_SUMMARY_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (_study.isAncillaryStudy())
            {
                List<Container> containers = new ArrayList<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                Map<Container, SQLFragment> filterFragments = getAncillaryStudyFilterFragments(sourceStudyContainer);
                return StudyService.get().getSpecimenSummaryTableUnion(this, containers, filterFragments, _dontAliasColumns, false);
            }
            SpecimenSummaryTable ret = new SpecimenSummaryTable(this);
            return ret;
        }
        if (SPECIMEN_DETAIL_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (_study.isAncillaryStudy())
            {
                List<Container> containers = new ArrayList<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                Map<Container, SQLFragment> filterFragments = getAncillaryStudyFilterFragments(sourceStudyContainer);
                return StudyService.get().getSpecimenDetailTableUnion(this, containers, filterFragments, _dontAliasColumns, false);
            }
            SpecimenDetailTable ret = new SpecimenDetailTable(this);
            return ret;
        }
        if (SPECIMEN_WRAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            if (_study.isAncillaryStudy())
            {
                List<Container> containers = new ArrayList<>();
                containers.add(_study.getContainer());
                Container sourceStudyContainer = _study.getSourceStudy().getContainer();
                containers.add(sourceStudyContainer);

                Map<Container, SQLFragment> filterFragments = getAncillaryStudyFilterFragments(sourceStudyContainer);
                return StudyService.get().getSpecimenWrapTableUnion(this, containers, filterFragments, _dontAliasColumns, false);
            }
            SpecimenWrapTable ret = new SpecimenWrapTable(this);
            return ret;
        }
        if ("SpecimenVialCount".equalsIgnoreCase(name))
        {
            SpecimenVialCountTable ret = new SpecimenVialCountTable(this);
            return ret;
        }
        if (SPECIMEN_EVENT_TABLE_NAME.equalsIgnoreCase(name))
        {
            SpecimenEventTable ret = new SpecimenEventTable(this);
            return ret;
        }
        if (StudyService.get().getSubjectVisitTableName(getContainer()).equalsIgnoreCase(name) && _study.getTimepointType() != TimepointType.CONTINUOUS)
        {
            ParticipantVisitTable ret = new ParticipantVisitTable(this, false);
            return ret;
        }
        if ("SpecimenRequest".equalsIgnoreCase(name))
        {
            SpecimenRequestTable ret = new SpecimenRequestTable(this);
            return ret;
        }
        if ("SpecimenRequestStatus".equalsIgnoreCase(name))
        {
            SpecimenRequestStatusTable ret = new SpecimenRequestStatusTable(this);
            return ret;
        }
        if ("Visit".equalsIgnoreCase(name) && _study.getTimepointType() != TimepointType.CONTINUOUS)
        {
            VisitTable ret = new VisitTable(this);
            return ret;
        }
        if ("DataSets".equalsIgnoreCase(name))
        {
            DataSetsTable ret = new DataSetsTable(this);
            return ret;
        }
        if (DataSetColumnsTable.NAME.equalsIgnoreCase(name))
        {
            DataSetColumnsTable ret = new DataSetColumnsTable(this);
            return ret;
        }
        if (QCSTATE_TABLE_NAME.equalsIgnoreCase(name))
        {
            FilteredTable ret = new QCStateTable(this);
            return ret;
        }
        if ("SpecimenAdditive".equalsIgnoreCase(name))
        {
            FilteredTable ret = new AdditiveTypeTable(this);
            return ret;
        }
        if ("SpecimenDerivative".equalsIgnoreCase(name))
        {
            FilteredTable ret = new DerivativeTypeTable(this);
            return ret;
        }
        if ("SpecimenPrimaryType".equalsIgnoreCase(name))
        {
            FilteredTable ret = new PrimaryTypeTable(this);
            return ret;
        }
        if ("SpecimenComment".equalsIgnoreCase(name))
        {
            FilteredTable ret = new SpecimenCommentTable(this);
            return ret;
        }
        if ("VialRequest".equalsIgnoreCase(name))
        {
            FilteredTable ret = new VialRequestTable(this);
            return ret;
        }
        if (STUDY_SNAPSHOT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudySnapshotTable(this);
        }
        if (PRODUCT_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyProductDomainKind domainKind = new StudyProductDomainKind();
            Domain domain = domainKind.ensureDomain(getContainer(), getUser(), PRODUCT_TABLE_NAME);

            return new StudyProductTable(domain, this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (PRODUCT_ANTIGEN_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyProductAntigenDomainKind domainKind = new StudyProductAntigenDomainKind();
            Domain domain = domainKind.ensureDomain(getContainer(), getUser(), PRODUCT_ANTIGEN_TABLE_NAME);

            return new StudyProductAntigenTable(domain, this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (TREATMENT_PRODUCT_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyTreatmentProductDomainKind domainKind = new StudyTreatmentProductDomainKind();
            Domain domain = domainKind.ensureDomain(getContainer(), getUser(), TREATMENT_PRODUCT_MAP_TABLE_NAME);

            return new StudyTreatmentProductTable(domain, this, isDataspace() ? new ContainerFilter.AllInProject(getUser()) : null);
        }
        if (TREATMENT_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyTreatmentDomainKind domainKind = new StudyTreatmentDomainKind();
            Domain domain = domainKind.ensureDomain(getContainer(), getUser(), TREATMENT_TABLE_NAME);

            return new StudyTreatmentTable(domain, this, isDataspace() ? new ContainerFilter.AllInProject(getUser()) : null);
        }
        if (TREATMENT_VISIT_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyTreatmentVisitMapTable(this);
        }
        if (SpecimenPivotByPrimaryType.PIVOT_BY_PRIMARY_TYPE.equalsIgnoreCase(name))
        {
            return new SpecimenPivotByPrimaryType(this);
        }
        if (SpecimenPivotByDerivativeType.PIVOT_BY_DERIVATIVE_TYPE.equalsIgnoreCase(name))
        {
            return new SpecimenPivotByDerivativeType(this);
        }
        if (SpecimenPivotByRequestingLocation.PIVOT_BY_REQUESTING_LOCATION.equalsIgnoreCase(name))
        {
            return new SpecimenPivotByRequestingLocation(this);
        }
        if (LOCATION_SPECIMEN_LIST_TABLE_NAME.equalsIgnoreCase(name))
        {
            FilteredTable ret = new LocationSpecimenListTable(this);
            return ret;
        }
        if (PERSONNEL_TABLE_NAME.equalsIgnoreCase(name))
        {
            StudyPersonnelDomainKind domainKind = new StudyPersonnelDomainKind();
            Domain domain = domainKind.ensureDomain(getContainer(), getUser(), PERSONNEL_TABLE_NAME);

            return new StudyPersonnelTable(domain, this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (OBJECTIVE_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new StudyObjectiveTable(this);
        }
        if (ASSAY_SPECIMEN_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new AssaySpecimenTable(this);
        }
        if (ASSAY_SPECIMEN_VISIT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new AssaySpecimenVisitTable(this);
        }
        if (VISIT_TAG_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new VisitTagTable(this, isDataspaceProject() ? new ContainerFilter.Project(getUser()) : null);
        }
        if (VISIT_TAG_MAP_TABLE_NAME.equalsIgnoreCase(name))
        {
            return new VisitTagMapTable(this, null);
        }
        if (name.startsWith(VISUALIZTION_VISIT_TAG_TABLE_NAME))
        {
            // Name is encoded with useProtocolDay boolean, interval, tag name
            String params = name.replace(VISUALIZTION_VISIT_TAG_TABLE_NAME, "");
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
            String interval = params.substring(0, hyphenIndex);
            String tagName = params.substring(hyphenIndex + 1);

            return new VisualizationVisitTagTable(getStudy(), getUser(), tagName, useProtocolDay, interval);
        }

        // Might be a dataset
        DataSetDefinition dsd = getDatasetDefinitionByQueryName(name);

        if (null != dsd)
        {
            try
            {
                return new DataSetTableImpl(this, dsd);
            }
            catch (UnauthorizedException e)
            {
                return null;
            }
        }
        
        return null;
    }


    // Simple helper to keep us consistent... try getting by name first, then by label
    private @Nullable DataSetDefinition getDatasetDefinitionByQueryName(String queryName)
    {
        assert _study != null : "Attempt to get datasets without a study";
        if (null == queryName)
            return null;

        return StudyManager.getInstance().getDatasetDefinitionByQueryName(_study, queryName);
    }

    private Map<Container, SQLFragment> getAncillaryStudyFilterFragments(Container sourceStudyContainer)
    {
        assert _study.isAncillaryStudy();       // Don't call if it's not
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

    // Null if there's no alias dataset configured, otherwise a "skinny" table with just Participant, Source, and Alias,
    // in that order but having the configured names.
    @Nullable public TableInfo getParticipantAliasesTable()
    {
        StudyImpl study = getStudy();

        if (null != study)
        {
            Integer id = study.getParticipantAliasDatasetId();

            if (null != id)
            {
                DataSetDefinition def = study.getDataSet(id);

                if (null != def)
                {
                    DataSetTableImpl datasetTable = new DataSetTableImpl(this, def);

                    String aliasName = study.getParticipantAliasProperty();
                    String sourceName = study.getParticipantAliasSourceProperty();

                    ColumnInfo aliasColumn = datasetTable.getColumn(aliasName);
                    ColumnInfo sourceColumn = datasetTable.getColumn(sourceName);

                    if (null != aliasColumn && null != sourceColumn)
                    {
                        FilteredTable aliasTable = new FilteredTable<>(datasetTable, this);

                        aliasTable.addWrapColumn(datasetTable.getColumn(study.getSubjectColumnName()));
                        aliasTable.addWrapColumn(aliasColumn);
                        aliasTable.addWrapColumn(sourceColumn);

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
        if (DataSetQueryView.DATAREGION.equals(dataRegionName))
            return new DataSetQuerySettings(dataRegionName);

        return super.createQuerySettings(dataRegionName, queryName, viewName);
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        if (getStudy() != null)
        {
            if ("SpecimenDetail".equalsIgnoreCase(settings.getQueryName()))
            {
                return SpecimenQueryView.createView(context, settings, SpecimenQueryView.ViewType.VIALS);
            }

            DataSetDefinition dsd = getDatasetDefinitionByQueryName(settings.getQueryName());
            // Check for permission before deciding to treat the request as a dataset
            if (dsd != null && dsd.canRead(getUser()))
            {
                // Issue 18787: if dataset name and label differ, use the name for the queryName
                if (!settings.getQueryName().equals(dsd.getName()))
                    settings.setQueryName(dsd.getName());

                if (!(settings instanceof DataSetQuerySettings))
                    settings = new DataSetQuerySettings(settings);

                return new DataSetQueryView(this, (DataSetQuerySettings)settings, errors);
            }
        }
        return super.createView(context, settings, errors);
    }

    @Nullable
    public StudyImpl getStudy()
    {
        return _study;
    }

    public String decideTableName(DataSetDefinition dsd)
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
        
        DataSetDefinition def = getDatasetDefinitionByQueryName(queryName);
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
    ContainerFilter getDefaultContainerFilter()
    {
        return ContainerFilter.CURRENT;
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
    public TablePackage getTablePackage(AbstractContext ctx, StudyQuerySchema projectSchema, String tableName)
    {
        TableInfo tableInfo;
        Container container;
        boolean isProjectLevel = false;
        if (ctx.isDataspaceProject() && StudyQuerySchema.isDataspaceProjectTable(tableName))
        {
            tableInfo = projectSchema.getTable(tableName);
            container = ctx.getProject();
            isProjectLevel = true;
        }
        else
        {
            tableInfo = this.getTable(tableName);
            container = ctx.getContainer();
        }
        return new TablePackage(tableInfo, container, isProjectLevel);
    }

    private static Set<String> _dataspaceProjectLevelTables = new HashSet<>();
    private static Set<String> _dataspaceFolderLevelTables = new HashSet<>();
    static
    {
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_GENES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_ROUTES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_SUB_TYPES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_ASSAYS_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_LABS_TABLE_NAME);
        _dataspaceProjectLevelTables.add(STUDY_DESIGN_UNITS_TABLE_NAME);
        _dataspaceProjectLevelTables.add(PRODUCT_TABLE_NAME);
        _dataspaceProjectLevelTables.add(PRODUCT_ANTIGEN_TABLE_NAME);
        _dataspaceProjectLevelTables.add(PERSONNEL_TABLE_NAME);
        _dataspaceProjectLevelTables.add(VISIT_TAG_TABLE_NAME);

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
}
