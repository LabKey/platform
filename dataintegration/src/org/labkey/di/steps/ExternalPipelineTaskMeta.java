package org.labkey.di.steps;

import org.apache.xmlbeans.XmlException;
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

    @Override
    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        if (transformXML.getExternalTaskId() != null)
            try
            {
                externalTaskId = new TaskId(transformXML.getExternalTaskId());
            }
            catch (ClassNotFoundException e)
            {
                throw new XmlException("Bad external taskId " + transformXML.getExternalTaskId(), e);
            }
        else throw new XmlException("Pipeline TaskId is required");
     }

    public TaskId getExternalTaskId()
    {
        return externalTaskId;
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
