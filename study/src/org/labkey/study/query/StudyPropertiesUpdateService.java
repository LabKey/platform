/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.DateUtil;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.visitmanager.RelativeDateVisitManager;
import org.labkey.study.visitmanager.VisitManager;

import java.util.Date;
import java.util.Map;

/**
 * User: jgarms
 * Date: Aug 11, 2008
 */
public class StudyPropertiesUpdateService extends AbstractQueryUpdateService
{
    public StudyPropertiesUpdateService(TableInfo table)
    {
        super(table);
    }


    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws QueryUpdateServiceException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new QueryUpdateServiceException("No study found.");
        StudyQuerySchema querySchema = StudyQuerySchema.createSchema(study, user, true);
        TableInfo queryTableInfo = querySchema.getTable("StudyProperties", null);
        if (null == queryTableInfo)
            throw new QueryUpdateServiceException("StudyProperties table not found.");
        return new TableSelector(queryTableInfo).getObject(container.getId(), Map.class);
    }


    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @Nullable Map<String, Object> oldRow) throws ValidationException, QueryUpdateServiceException
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            throw new QueryUpdateServiceException("No study found.");
        StudyPropertiesTable table = (StudyPropertiesTable)getQueryTable();

        // NOTE using updateTabDelimited() or updateStatement() is tricky, because they will NULL out all unspecified columns

        // save properties
        study.savePropertyBag(row);

        // update the base table, but only some columns StudyPropertiesTable
        CaseInsensitiveHashMap<Object> updateRow = new CaseInsensitiveHashMap<>();
        boolean recomputeStartDates = false;
        Date originalStartDate = null;
        boolean recalculateTimepoints = false;
        for (Map.Entry<String,Object> entry : row.entrySet())
        {
            ColumnInfo c = table.getColumn(entry.getKey());
            if (null != c)
            {
                if ("TimepointType".equalsIgnoreCase(c.getName()))
                {
                    if (entry.getValue() != null)
                    {
                        // Brand new studies can be set to whatever type the user likes
                        if (study.isEmptyStudy())
                        {
                            updateRow.put(entry.getKey(), entry.getValue());
                        }
                        else
                        {
                            try
                            {
                                // Existing studies only support certain changes to the timepoint type
                                TimepointType newTimepointType = TimepointType.valueOf(entry.getValue().toString());
                                if (newTimepointType != study.getTimepointType())
                                {
                                    study.getTimepointType().validateTransition(newTimepointType);
                                    updateRow.put(entry.getKey(), entry.getValue());
                                    recalculateTimepoints = true;
                                }
                            }
                            catch (IllegalArgumentException ignored)
                            {
                                throw new ValidationException("Invalid TimepointType: " + entry.getValue());
                            }
                        }
                    }
                }
                else if (c.isUserEditable())
                {
                    updateRow.put(entry.getKey(), entry.getValue());

                    if (entry.getValue() != null && c.getName().equalsIgnoreCase("StartDate"))
                    {
                        // Translate both values to new Date objects to avoid Timestamp/Date comparison problems - issue 40166
                        Date newDate = new Date(DateUtil.parseDateTime(container, entry.getValue().toString()));
                        Date oldDate = study.getStartDate() != null ? new Date(study.getStartDate().getTime()) : null;
                        recomputeStartDates = !newDate.equals(oldDate);

                        if (recomputeStartDates)
                        {
                            // Stash the original value to use later
                            originalStartDate = study.getStartDate();
                        }
                    }
                }
            }
        }

        if (!updateRow.isEmpty())
            Table.update(user, table.getRealTable(), updateRow, study.getContainer().getId());

        StudyManager.getInstance().clearCaches(container, false);

        // Reload the study object after flushing caches
        study = StudyManager.getInstance().getStudy(study.getContainer());

        if (recalculateTimepoints)
        {
            // Blow away all of the existing visit info and rebuild it
            VisitManager manager = StudyManager.getInstance().getVisitManager(study);
            StudySchema schema = StudySchema.getInstance();

            SQLFragment sqlFragParticipantVisit = new SQLFragment("DELETE FROM " + schema.getTableInfoParticipantVisit() + " WHERE Container = ?").add(study.getContainer());
            new SqlExecutor(schema.getSchema()).execute(sqlFragParticipantVisit);
            SQLFragment sqlFragVisitMap = new SQLFragment("DELETE FROM " + schema.getTableInfoVisitMap() + " WHERE Container = ?").add(study.getContainer());
            new SqlExecutor(schema.getSchema()).execute(sqlFragVisitMap);
            SQLFragment sqlFragVisit = new SQLFragment("DELETE FROM " + schema.getTableInfoVisit() + " WHERE Container = ?").add(study.getContainer());
            new SqlExecutor(schema.getSchema()).execute(sqlFragVisit);

            manager.updateParticipantVisits(user, study.getDatasets());
        }
        else if (recomputeStartDates && study.getTimepointType() == TimepointType.DATE)
        {
            // Only recalculate relative to the start date
            RelativeDateVisitManager visitManager = (RelativeDateVisitManager) StudyManager.getInstance().getVisitManager(study);
            visitManager.recomputeDates(originalStartDate, user);
        }

        return getRow(user, container, null);
    }


    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
    {
        throw new UnsupportedOperationException("You cannot delete all of a Study's properties");
    }


    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws ValidationException, QueryUpdateServiceException
    {
        return updateRow(user, container, row, null);
    }
}
