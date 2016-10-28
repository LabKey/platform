/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.di.steps;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.query.SchemaKey;
import org.labkey.etl.xml.TransformType;

/**
 * User: tgaluhn
 * Date: 11/18/2014
 *
 * Allow certain types of regular pipeline tasks to be registered within an ETL context.
 * So far TransformPipelineJob has been modified to implement FileAnalysisJobSupport; this provides
 * basic support to run instances of CommandTask as steps in an ETL
 */
public class ExternalPipelineTaskMeta extends StepMetaImpl
{
    private TaskId externalTaskId;
    private TaskId parentPipelineTaskId;

    @Override
    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        if (transformXML.getExternalTaskId() != null)
        {
            try
            {
                externalTaskId = new TaskId(transformXML.getExternalTaskId());
            }
            catch (ClassNotFoundException e)
            {
                throw new XmlException("Bad external taskId " + transformXML.getExternalTaskId(), e);
            }
        }
        else
            throw new XmlException("Pipeline ExternalTaskId is required");

        if (transformXML.getParentPipelineTaskId() != null)
        {
            try
            {
                parentPipelineTaskId = new TaskId(transformXML.getParentPipelineTaskId());
            }
            catch (ClassNotFoundException e)
            {
                throw new XmlException("Bad parent pipeline taskId " + transformXML.getParentPipelineTaskId(), e);
            }
            // Verify this pipeline exists. But can't persist the TaskPipeline as property because it doesn't serialize
            if (PipelineJobService.get().getTaskPipeline(parentPipelineTaskId) == null)
                throw new XmlException("No pipeline found for parent pipeline taskId " + transformXML.getParentPipelineTaskId());
        }
     }

    public TaskId getExternalTaskId()
    {
        return externalTaskId;
    }

    public TaskId getParentPipelineTaskId()
    {
        return parentPipelineTaskId;
    }

    @Override
    public SchemaKey getSourceSchema()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSourceQuery()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SchemaKey getTargetSchema()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTargetQuery()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUseSource()
    {
        throw new UnsupportedOperationException();
    }

}
