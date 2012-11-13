/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.*;
import org.springframework.validation.BindException;

import java.util.*;

public class StudyQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "study";
    public static final String SCHEMA_DESCRIPTION = "Contains all data related to the study, including subjects, cohorts, visits, datasets, specimens, etc.";
    public static final String SIMPLE_SPECIMEN_TABLE_NAME = "SimpleSpecimen";
    public static final String SPECIMEN_DETAIL_TABLE_NAME = "SpecimenDetail";

    @Nullable // if no study defined in this container
    final StudyImpl _study;

    final boolean _mustCheckPermissions;

    private Map<Integer, List<Double>> _datasetSequenceMap;
    public static final String STUDY_DATA_TABLE_NAME = "StudyData";
    public static final String QCSTATE_TABLE_NAME = "QCState";

    public StudyQuerySchema(StudyImpl study, User user, boolean mustCheckPermissions)
    {
        super(SCHEMA_NAME, SCHEMA_DESCRIPTION, user, study.getContainer(), StudySchema.getInstance().getSchema());
        _study = study;
        _mustCheckPermissions = mustCheckPermissions;
    }

    // Quick fix for all the places that don't bother to check if the study exists... throw NotFound instead of NPE
    private static StudyImpl throwIfNull(StudyImpl study)
    {
        if (null == study)
            throw new NotFoundException("This folder does not contain a study");

        return study;
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


    static StudyQuerySchema createSchemaWithoutStudy(Container c, User u)
    {
        return new StudyQuerySchema(c, u);
    }


    // TODO remove this when UserSchema implements caching
    // see https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=14369
    // we're not quite ready for general table caching, so just cache BaseSpecimenPivotTable
    Map<Pair<String,Boolean>,Object> pivotCache = new HashMap<Pair<String, Boolean>, Object>();

    @Override
    public Object _getTableOrQuery(String name, boolean includeExtraMetadata, boolean forWrite, Collection<QueryException> errors)
    {
        Pair<String,Boolean> key = new Pair(name.toLowerCase(),includeExtraMetadata);
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
        return !getMustCheckPermissions() || super.canReadSchema();
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
        Set<String> ret = new LinkedHashSet<String>();

        // Always add StudyProperties, even if we have no study
        ret.add("StudyProperties");
        if (_study != null)
        {
            // All these require studies defined
            ret.add(STUDY_DATA_TABLE_NAME);
            ret.add(StudyService.get().getSubjectTableName(getContainer()));
            ret.add("Site");
            if (_study.getTimepointType() != TimepointType.CONTINUOUS)
                ret.add("Visit");

            if (_study.getTimepointType() != TimepointType.CONTINUOUS)
                ret.add(StudyService.get().getSubjectVisitTableName(getContainer()));

            ret.add("SpecimenEvent");
            ret.add(SPECIMEN_DETAIL_TABLE_NAME);
            ret.add("SpecimenSummary");
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
            ret.add("DataSetColumns");

            // Only show cohorts if the user has permission
            if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                ret.add("Cohort");

            ret.add("QCState");

            // Subject category/group tables:
            ret.add(StudyService.get().getSubjectCategoryTableName(getContainer()));
            ret.add(StudyService.get().getSubjectGroupTableName(getContainer()));
            ret.add(StudyService.get().getSubjectGroupMapTableName(getContainer()));

            // specimen report pivots
            ret.add(SpecimenPivotByPrimaryType.PIVOT_BY_PRIMARY_TYPE);
            ret.add(SpecimenPivotByDerivativeType.PIVOT_BY_DERIVATIVE_TYPE);
            ret.add(SpecimenPivotByRequestingLocation.PIVOT_BY_REQUESTING_LOCATION);

            // Add only datasets that the user can read
            User user = getUser();
            for (DataSetDefinition dsd : _study.getDataSets())
            {
                boolean canRead = dsd.canRead(user);
                if (dsd.getLabel() == null || !canRead)
                    continue;
                ret.add(dsd.getLabel());
            }
        }
        return ret;
    }

    public Map<String, DataSetDefinition> getDataSetDefinitions()
    {
        Map<String, DataSetDefinition> ret = new LinkedHashMap<String, DataSetDefinition>();
        assert _study != null : "Attempt to get datasets without a study";
        if (_study != null)
        {
            for (DataSetDefinition dsd : _study.getDataSets())
            {
                if (dsd.getLabel() == null)
                    continue;
                ret.put(dsd.getLabel(), dsd);
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
    public DataSetTableImpl createDataSetTableInternal(DataSetDefinition definition)
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
        if ("StudyProperties".equalsIgnoreCase(name))
        {
            StudyPropertiesTable ret = new StudyPropertiesTable(this);
            return ret;
        }

        // Expose the simplified specimen table even if there's no study in this container. The caller may
        // want to set a container filter to look at specimens across the entire site
        if (SIMPLE_SPECIMEN_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createSimpleSpecimenTable();
        }
        if ("Vial".equalsIgnoreCase(name))
        {
            return new VialTable(this);
        }
        if ("Site".equalsIgnoreCase(name))
        {
            SiteTable ret = new SiteTable(this);
            return ret;
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
        if ("SpecimenSummary".equalsIgnoreCase(name))
        {
            SpecimenSummaryTable ret = new SpecimenSummaryTable(this);
            return ret;
        }
        if (SPECIMEN_DETAIL_TABLE_NAME.equalsIgnoreCase(name))
        {
            SpecimenDetailTable ret = new SpecimenDetailTable(this);
            return ret;
        }
        if ("SpecimenVialCount".equalsIgnoreCase(name))
        {
            SpecimenVialCountTable ret = new SpecimenVialCountTable(this);
            return ret;
        }
        if ("SpecimenEvent".equalsIgnoreCase(name))
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
        if ("DataSetColumns".equalsIgnoreCase(name))
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

        //might be a dataset--try getting by name first, then by label
        DataSetDefinition dsd = getDataSetDefinitionByName(name);
        if (dsd == null)
            dsd = getDataSetDefinitions().get(name);
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
            DataSetDefinition dsd = getDataSetDefinitions().get(settings.getQueryName());
            if (dsd != null)
            {
                if (!(settings instanceof DataSetQuerySettings))
                    settings = new DataSetQuerySettings(settings);

                return new DataSetQueryView(this, (DataSetQuerySettings)settings, errors);
            }
        }
        return super.createView(context, settings, errors);
    }

    public SimpleSpecimenTable createSimpleSpecimenTable()
    {
        return new SimpleSpecimenTable(this, !_mustCheckPermissions);
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
        
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, queryName);
        if (def != null)
        {
            if (def.canRead(getUser()))
                return def.getTypeURI();
            else
                throw new RuntimeException("User does not have permission to read that dataset");
        }

        return null;
    }


    @Override
    public NavTree getSchemaBrowserLinks(User user)
    {
        NavTree root = super.getSchemaBrowserLinks(user);
        if (getContainer().hasPermission(user, AdminPermission.class))
            root.addChild("Manage datasets", new ActionURL(StudyController.ManageTypesAction.class, getContainer()));
        return root;
    }
}
