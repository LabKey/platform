/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.pipeline.trigger;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.trigger.PipelineTriggerConfig;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.pipeline.api.PipelineSchema;

import java.nio.file.Path;
import java.util.Date;

public class PipelineTriggerManager
{
    private static final PipelineTriggerManager INSTANCE = new PipelineTriggerManager();
    private PipelineTriggerManager(){}

    public static PipelineTriggerManager getInstance()
    {
        return INSTANCE;
    }

    @Nullable
    public Date getLastTriggeredTime(PipelineTriggerConfig config, Path filePath)
    {
        return getLastTriggeredTime(config.lookupContainer(), config.getRowId(), filePath);
    }

    @Nullable
    public Date getLastTriggeredTime(Container container, int triggerConfigId, Path filePath)
    {
        TriggeredFile triggeredFile = getTriggeredFile(container, triggerConfigId, filePath);
        return triggeredFile != null ? triggeredFile.getLastRun() : null;
    }

    private TriggeredFile getTriggeredFile(Container container, int triggerConfigId, Path filePath)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("TriggerId"), triggerConfigId);
        filter.addCondition(FieldKey.fromParts("FilePath"), filePath.toAbsolutePath().toString());

        return new TableSelector(PipelineSchema.getInstance().getTableInfoTriggeredFiles(), filter, null).getObject(TriggeredFile.class);
    }

    public void setTriggeredTime(PipelineTriggerConfig config, User user, Path filePath, Date date)
    {
        setTriggeredTime(config.lookupContainer(), user, config.getRowId(), filePath, date);
    }

    public void setTriggeredTime(Container container, User user, int triggerConfigId, Path filePath, Date date)
    {
        TriggeredFile triggeredFile = getTriggeredFile(container, triggerConfigId, filePath);

        if (triggeredFile != null)
        {
            triggeredFile.setLastRun(date);
            Table.update(user, PipelineSchema.getInstance().getTableInfoTriggeredFiles(), triggeredFile, triggeredFile.getRowId());
        }
        else
        {
            triggeredFile = new TriggeredFile();
            triggeredFile.setContainer(container);
            triggeredFile.setTriggerId(triggerConfigId);
            triggeredFile.setFilePath(filePath.toAbsolutePath().toString());
            triggeredFile.setLastRun(date);

            Table.insert(user, PipelineSchema.getInstance().getTableInfoTriggeredFiles(), triggeredFile);
        }
    }

    /**
     * Clean up triggered records for a specified trigger configuration
     */
    public void purgeTriggeredEntries(PipelineTriggerConfig config)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(PipelineSchema.getInstance().getTableInfoTriggeredFiles(), "")
                .append(" WHERE TriggerId = ?").add(config.getRowId());

        new SqlExecutor(PipelineSchema.getInstance().getSchema().getScope()).execute(sql);
    }

    public static class TriggeredFile
    {
        private int _rowId;
        private Container _container;
        private int _triggerId;
        private String _filePath;
        private Date _lastRun;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public Container getContainer()
        {
            return _container;
        }

        public void setContainer(Container container)
        {
            _container = container;
        }

        public int getTriggerId()
        {
            return _triggerId;
        }

        public void setTriggerId(int triggerId)
        {
            _triggerId = triggerId;
        }

        public String getFilePath()
        {
            return _filePath;
        }

        public void setFilePath(String filePath)
        {
            _filePath = filePath;
        }

        public Date getLastRun()
        {
            return _lastRun;
        }

        public void setLastRun(Date lastRun)
        {
            _lastRun = lastRun;
        }
    }
}
