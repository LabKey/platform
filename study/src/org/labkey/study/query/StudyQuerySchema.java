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

package org.labkey.study.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;

import java.util.*;

public class StudyQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "study";
    public static final String SCHEMA_DESCRIPTION = "Contains all data related to the study, including subjects, cohorts, visits, datasets, specimens, etc.";

    @Nullable // if no study defined in this container
    final StudyImpl _study;

    boolean _mustCheckPermissions;

    private Map<Integer, List<Double>> _datasetSequenceMap;
    public static final String SIMPLE_SPECIMEN_TABLE_NAME = "SimpleSpecimen";

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


    static StudyQuerySchema createSchemaWithoutStudy(Container c, User u)
    {
        return new StudyQuerySchema(c, u);
    }


    @Override
    protected boolean canReadSchema()
    {
        return !getMustCheckPermissions() || super.canReadSchema();
    }


    public Set<String> getTableNames()
    {
        Set<String> ret = new LinkedHashSet<String>();

        // Always add StudyProperties, even if we have no study
        ret.add("StudyProperties");
        if (_study != null)
        {
            // All these require studies defined
            ret.add("StudyData");
            ret.add(StudyService.get().getSubjectTableName(getContainer()));
            ret.add("Site");
            if (_study.getTimepointType() != TimepointType.ABSOLUTE_DATE)
                ret.add("Visit");
            ret.add("SpecimenEvent");
            ret.add("SpecimenDetail");
            ret.add("SpecimenSummary");
            ret.add("SpecimenVialCount");
            ret.add(SIMPLE_SPECIMEN_TABLE_NAME);
            ret.add("SpecimenRequest");
            ret.add("SpecimenRequestStatus");
            ret.add("VialRequest");
            if (_study.getTimepointType() != TimepointType.ABSOLUTE_DATE)
                ret.add(StudyService.get().getSubjectVisitTableName(getContainer()));
            ret.add("DataSets");
            ret.add("DataSetColumns");

            // Only show cohorts if the user has permission
            if (StudyManager.getInstance().showCohorts(getContainer(), getUser()))
                ret.add("Cohort");

            ret.add("QCState");
            ret.add("SpecimenAdditive");
            ret.add("SpecimenDerivative");
            ret.add("SpecimenPrimaryType");
            ret.add("SpecimenComment");

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
        for (DataSetDefinition dsd : _study.getDataSets())
        {
            if (dsd.getLabel() == null)
                continue;
            ret.put(dsd.getLabel(), dsd);
        }
        return ret;
    }

    @Nullable
    public DataSetDefinition getDataSetDefinitionByName(String name)
    {
        assert _study != null : "Attempt to get datasets without a study";
        for (DataSetDefinition dsd : _study.getDataSets())
        {
            if (name.equalsIgnoreCase(dsd.getName()))
                return dsd;
        }
        return null;
    }

    public FilteredTable getDataSetTable(DataSetDefinition definition)
    {
        try
        {
            DataSetTable ret = new DataSetTable(this, definition);
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

        if (_study == null)
            return null;

        if ("StudyData".equalsIgnoreCase(name))
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
            ParticipantTable ret = new ParticipantTable(this);
            return ret;
        }
        if ("Site".equalsIgnoreCase(name))
        {
            SiteTable ret = new SiteTable(this);
            return ret;
        }
        if ("SpecimenSummary".equalsIgnoreCase(name))
        {
            SpecimenSummaryTable ret = new SpecimenSummaryTable(this);
            return ret;
        }
        if ("SpecimenDetail".equalsIgnoreCase(name))
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
        if (StudyService.get().getSubjectVisitTableName(getContainer()).equalsIgnoreCase(name) && _study.getTimepointType() != TimepointType.ABSOLUTE_DATE)
        {
            ParticipantVisitTable ret = new ParticipantVisitTable(this);
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
        if ("Visit".equalsIgnoreCase(name) && _study.getTimepointType() != TimepointType.ABSOLUTE_DATE)
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
        if ("QCState".equalsIgnoreCase(name))
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

        //might be a dataset--try getting by name first, then by label
        DataSetDefinition dsd = getDataSetDefinitionByName(name);
        if (dsd == null)
            dsd = getDataSetDefinitions().get(name);
        if (null != dsd)
            return getDataSetTable(dsd);
        
        return null;
    }

    public SimpleSpecimenTable createSimpleSpecimenTable()
    {
        return new SimpleSpecimenTable(this);
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

}
