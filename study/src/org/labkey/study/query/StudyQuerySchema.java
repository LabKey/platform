/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.study.Study;
import org.labkey.api.study.DataSet;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.StudyImpl;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StudyQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "study";

    @Nullable // if no study defined in this container
    final StudyImpl _study;

    boolean _mustCheckPermissions;

    private Map<Integer, List<Double>> _datasetSequenceMap;

    public StudyQuerySchema(StudyImpl study, User user, boolean mustCheckPermissions)
    {
        super(SCHEMA_NAME, user, study.getContainer(), StudySchema.getInstance().getSchema());
        _study = study;
        _mustCheckPermissions = mustCheckPermissions;
    }

    /**
     * This c-tor is for schemas that have no study defined -- _study is null!
     */
    private StudyQuerySchema(Container c, User user)
    {
        super(SCHEMA_NAME, user, c, StudySchema.getInstance().getSchema());
        _study = null;
        _mustCheckPermissions = true;
    }

    static StudyQuerySchema createSchemaWithoutStudy(Container c, User u)
    {
        return new StudyQuerySchema(c, u);
    }

    public Set<String> getTableNames()
    {
        Set<String> ret = new LinkedHashSet<String>();

        // Always add StudyProperties, even if we have no study
        ret.add("StudyProperties");
        if (_study != null)
        {
            // All these require studies defined
            ret.add("Participant");
            ret.add("Site");
            ret.add("Visit");
            ret.add("SpecimenEvent");
            ret.add("SpecimenDetail");
            ret.add("SimpleSpecimen");
            ret.add("SpecimenSummary");
            ret.add("SpecimenRequest");
            ret.add("SpecimenRequestStatus");
            ret.add("VialRequest");
            ret.add("ParticipantVisit");
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
        if ("StudyProperties".equals(name))
        {
            StudyPropertiesTable ret = new StudyPropertiesTable(this);
            return ret;
        }

        // Expose the simplified specimen table even if there's no study in this container. The caller may
        // want to set a container filter to look at specimens across the entire site
        if ("SimpleSpecimen".equalsIgnoreCase(name))
        {
            return new SimpleSpecimenTable(this);
        }

        if (_study == null)
            return null;

        if ("Cohort".equals(name))
        {
            CohortTable ret = new CohortTable(this);
            return ret;
        }
        if ("Participant".equals(name))
        {
            ParticipantTable ret = new ParticipantTable(this);
            return ret;
        }
        if ("Site".equals(name))
        {
            SiteTable ret = new SiteTable(this);
            return ret;
        }
        if ("SpecimenSummary".equals(name))
        {
            SpecimenSummaryTable ret = new SpecimenSummaryTable(this);
            return ret;
        }
        if ("SpecimenDetail".equals(name))
        {
            SpecimenDetailTable ret = new SpecimenDetailTable(this);
            return ret;
        }
        if ("SpecimenEvent".equals(name))
        {
            SpecimenEventTable ret = new SpecimenEventTable(this);
            return ret;
        }
        if ("ParticipantVisit".equals(name))
        {
            ParticipantVisitTable ret = new ParticipantVisitTable(this, null);
            return ret;
        }
        if ("SpecimenRequest".equals(name))
        {
            SpecimenRequestTable ret = new SpecimenRequestTable(this);
            return ret;
        }
        if ("SpecimenRequestStatus".equals(name))
        {
            RequestStatusTable ret = new RequestStatusTable(this);
            return ret;
        }
        if ("Visit".equals(name))
        {
            VisitTable ret = new VisitTable(this);
            return ret;
        }
        if ("DataSets".equals(name))
        {
            DataSetsTable ret = new DataSetsTable(this);
            return ret;
        }
        if ("DataSetColumns".equals(name))
        {
            DataSetColumnsTable ret = new DataSetColumnsTable(this);
            return ret;
        }
        if ("QCState".equals(name))
        {
            FilteredTable ret = new QCStateTable(this);
            return ret;
        }
        if ("SpecimenAdditive".equals(name))
        {
            FilteredTable ret = new AdditiveTypeTable(this);
            return ret;
        }
        if ("SpecimenDerivative".equals(name))
        {
            FilteredTable ret = new DerivativeTypeTable(this);
            return ret;
        }
        if ("SpecimenPrimaryType".equals(name))
        {
            FilteredTable ret = new PrimaryTypeTable(this);
            return ret;
        }
        if ("SpecimenComment".equals(name))
        {
            FilteredTable ret = new SpecimenCommentTable(this);
            return ret;
        }
        if ("VialRequest".equals(name))
        {
            FilteredTable ret = new VialRequestTable(this);
            return ret;
        }

        DataSetDefinition dsd = getDataSetDefinitions().get(name);
        if (dsd == null)
        {
            return null;
        }
        return getDataSetTable(dsd);
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
}
