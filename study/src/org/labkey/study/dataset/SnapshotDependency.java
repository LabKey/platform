/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
package org.labkey.study.dataset;


import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UnionTableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Nov 3, 2011
 * Time: 12:05:54 PM
 */
public abstract class SnapshotDependency
{
    private static final Logger _log = Logger.getLogger(SnapshotDependency.class);

    protected boolean isContainerValid(Container c)
    {
        if (c != null)
        {
            return ContainerManager.getForId(c.getId()) != null;
        }
        return false;
    }

    abstract List<QuerySnapshotDefinition> getDependencies(SourceDataType type);

    /**
     * Represents a data source that a query snapshot may be dependent on
     */
    public static class SourceDataType
    {
        private Type _type;
        private Object _value;
        private Container _container;

        enum Type {
            dataset,
            participantCategory,
        }

        public SourceDataType(Container container, Type type, Object value)
        {
            _container = container;
            _type = type;
            _value = value;
        }

        public Type getType()
        {
            return _type;
        }

        public Object getValue()
        {
            return _value;
        }

        public Container getContainer()
        {
            return _container;
        }
    }

    public static class Dataset extends SnapshotDependency
    {
        public List<QuerySnapshotDefinition> getDependencies(SourceDataType sourceData)
        {
            if (sourceData.getType() == SourceDataType.Type.dataset)
            {
                try
                {
                    org.labkey.api.study.Dataset dsDef = (org.labkey.api.study.Dataset)sourceData.getValue();

                    // check if container is still valid
                    Map<Integer, QuerySnapshotDefinition> dependencies = new HashMap<>();
                    if (isContainerValid(sourceData.getContainer()))
                    {
                        List<QuerySnapshotDefinition> snapshots = QueryService.get().getQuerySnapshotDefs(null, StudySchema.getInstance().getSchemaName());
                        if (!snapshots.isEmpty())
                        {
                            for (QuerySnapshotDefinition snapshot : snapshots)
                            {
                                if (!dependencies.containsKey(snapshot.getId()) && hasDependency(snapshot, dsDef))
                                {
                                    dependencies.put(snapshot.getId(), snapshot);
                                }
                            }
                        }
                    }
                    else
                        _log.info("Failed checking dependencies for container: " + dsDef.getContainer().getPath() + ", it has been deleted.");

                    return new ArrayList<>(dependencies.values());
                }
                catch (ConvertHelper.ContainerConversionException e)
                {
                    // ignore container conversion errors, most likely a race condition where the container has been deleted but
                    // the dependency thread continues to check (issue: 11659)
                    _log.info("Failed checking dependencies for container: " + sourceData.getContainer().getPath(), e);
                }
            }
            return Collections.emptyList();
        }

        // map of property uri to dataset id
        private static final Map<Integer, Map<String, String>> _snapshotPropertyMap = new HashMap<>();

        private boolean hasDependency(QuerySnapshotDefinition qsDef, @NotNull org.labkey.api.study.Dataset dsDef)
        {
            if (dsDef.getContainer().getId().equals(qsDef.getQueryTableContainerId()))
            {
                // dataset snapshots must have an underlying dataset definition defined
                StudyImpl study = StudyManager.getInstance().getStudy(qsDef.getContainer());
                DatasetDefinition def = StudyManager.getInstance().getDatasetDefinitionByName(study, qsDef.getName());
                if (def != null)
                {
                    if (qsDef.getQueryTableName().equals(dsDef.getName()))
                    {
                        return true;
                    }
                }
            }

            // if we can't match by 1:1 table relationship (ie: query or custom view), try the legacy property URI approach
            Domain d = PropertyService.get().getDomain(dsDef.getContainer(), dsDef.getTypeURI());
            if (d != null)
            {
                for (DomainProperty prop : d.getProperties())
                {
                    if (hasDependency(qsDef, prop.getPropertyURI()))
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean hasDependency(QuerySnapshotDefinition def, String propertyURI)
        {
            Map<String, String> propertyMap;

            synchronized (_snapshotPropertyMap)
            {
                if (!_snapshotPropertyMap.containsKey(def.getId()))
                {
                    propertyMap = new HashMap<>();
                    _snapshotPropertyMap.put(def.getId(), propertyMap);

                    // can't assume that the dependency check is coming from the same container that
                    // the snapshot is defined in.
                    ViewContext context = new ViewContext(DatasetSnapshotProvider.getViewContext(def, false));
                    context.setContainer(def.getContainer());

                    BindException errors = new NullSafeBindException(def, "snapshot");
                    QueryView view = DatasetSnapshotProvider.createQueryView(context, def, errors);
                    if (view != null)
                    {
                        TableInfo tinfo = view.getTable();

                        if (tinfo instanceof UnionTableInfo)
                        {
                            for (ColumnInfo info : ((UnionTableInfo)tinfo).getUnionColumns())
                            {
                                propertyMap.put(info.getPropertyURI(), info.getPropertyURI());
                            }
                        }
                        else
                        {
                            for (DisplayColumn dc : view.getDisplayColumns())
                            {
                                ColumnInfo info = dc.getColumnInfo();
                                if (info != null)
                                {
                                    propertyMap.put(info.getPropertyURI(), info.getPropertyURI());
                                }
                            }
                        }
                    }
                }
                else
                    propertyMap = _snapshotPropertyMap.get(def.getId());
            }
            return propertyMap.containsKey(propertyURI);
        }
    }

    public static class ParticipantCategoryDependency extends SnapshotDependency
    {
        @Override
        List<QuerySnapshotDefinition> getDependencies(SourceDataType sourceData)
        {
            if (sourceData.getType() == SourceDataType.Type.participantCategory)
            {
                ParticipantCategoryImpl category = (ParticipantCategoryImpl)sourceData.getValue();
                List<QuerySnapshotDefinition> dependencies = new ArrayList<>();
                List<Integer> groups = new ArrayList<>();

                // check if container is still valid
                if (isContainerValid(sourceData.getContainer()))
                {
                    for (QuerySnapshotDefinition def : QueryService.get().getQuerySnapshotDefs(sourceData.getContainer(), StudySchema.getInstance().getSchemaName()))
                    {
                        if (def.getParticipantGroups().contains(category.getRowId()))
                        {
                            dependencies.add(def);
                        }
                    }
                    return dependencies;
                }
            }
            return Collections.emptyList();
        }
    }
}
