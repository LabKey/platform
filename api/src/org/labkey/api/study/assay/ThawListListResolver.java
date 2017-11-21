/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DateUtil;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Sep 25, 2007
 */
public class ThawListListResolver extends AbstractParticipantVisitResolver
{
    private TableInfo _tableInfo;
    private final ParticipantVisitResolver _childResolver;

    public ThawListListResolver(Container runContainer, @Nullable Container targetStudyContainer,
                                Container listContainer, String schemaName, String queryName, User user, ParticipantVisitResolver childResolver) throws ExperimentException
    {
        super(runContainer, targetStudyContainer, user);
        _childResolver = childResolver;
        UserSchema schema = QueryService.get().getUserSchema(user, listContainer, schemaName);
        if (schema == null)
        {
            throw new ExperimentException("Could not find schema " + schemaName);
        }
        _tableInfo = schema.getTable(queryName);
        if (_tableInfo == null)
        {
            throw new ExperimentException("Could not find query " + queryName);
        }
    }

    @NotNull
    protected ParticipantVisit resolveParticipantVisit(String specimenID, String participantID, Double visitID, Date date, Container targetStudyContainer) throws ExperimentException
    {
        List<String> pkNames = _tableInfo.getPkColumnNames();
        FieldKey pkFieldKey;

        if (pkNames.size() == 1)
            pkFieldKey = FieldKey.fromParts(pkNames.get(0));
        else
            pkFieldKey = FieldKey.fromParts(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME);

        ColumnInfo col = _tableInfo.getColumn(pkFieldKey);
        Object convertedID;
        try
        {
            convertedID = ConvertUtils.convert(specimenID, col.getJavaObjectClass());
        }
        catch (ConversionException e)
        {
           throw new ThawListResolverException("Conversion exception converting to " + col.getJavaObjectClass().getSimpleName() + " resolving thaw list entry for specimenId: " + specimenID);
        }

        TableSelector selector = new TableSelector(_tableInfo, new SimpleFilter(pkFieldKey, convertedID), null);
        Map<String, Object>[] rows = selector.setForDisplay(true).getMapArray();

        if (rows.length == 0)
        {
            throw new ThawListResolverException("Can not resolve thaw list entry for specimenId: " + specimenID);
        }
        else if (rows.length > 1)
        {
            throw new ThawListResolverException("More than one thaw list entry was found for specimenId: " + specimenID);
        }
        else
        {
            Map<String, Object> dataRow = rows[0];
            String childSpecimenID = dataRow.get("SpecimenID") == null ? null : dataRow.get("SpecimenID").toString();
            String childParticipantID = dataRow.get("ParticipantID") == null ? null : dataRow.get("ParticipantID").toString();

            Double childVisitID = null;
            Object childVisitIDObject = dataRow.get("VisitID");
            if (childVisitIDObject instanceof Number)
            {
                childVisitID = ((Number) childVisitIDObject).doubleValue();
            }
            else if (childVisitIDObject instanceof String)
            {
                try
                {
                    childVisitID = Double.parseDouble((String) childVisitIDObject);
                }
                catch (NumberFormatException e)
                {
                    throw new ThawListResolverException("Can not convert VisitId value: " + childVisitIDObject + " to double for specimenId: " + specimenID);
                }
            }

            Date childDate = null;
            Object childDateObject = dataRow.get("Date");
            if (childDateObject instanceof Date)
            {
                childDate = (Date) childDateObject;
            }
            else if (childDateObject instanceof String)
            {
                try
                {
                    DateUtil.parseDateTime(getRunContainer(), (String) childDateObject);
                }
                catch (ConversionException e)
                {
                    throw new ThawListResolverException("Can not convert Date value: " + childDateObject + " to date for specimenId: " + specimenID);
                }
            }

            Container childTargetStudy = null;
            Object childTargetStudyObject = dataRow.get("TargetStudy");
            if (childTargetStudyObject != null)
            {
                Set<Study> studies = StudyService.get().findStudy(childTargetStudyObject, null);
                if (!studies.isEmpty())
                {
                    Study study = studies.iterator().next();
                    childTargetStudy = study != null ? study.getContainer() : null;
                }
            }

            return _childResolver.resolve(childSpecimenID, childParticipantID, childVisitID, childDate, childTargetStudy);
        }
    }
}
