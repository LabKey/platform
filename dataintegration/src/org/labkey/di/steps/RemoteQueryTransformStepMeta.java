/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.apache.xmlbeans.impl.values.XmlValueOutOfRangeException;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.query.SchemaKey;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.etl.xml.SchemaQueryType;
import org.labkey.etl.xml.TransformType;

/**
 * User: gktaylor
 * Date: 2013-10-08
 *
 * Metadata for a remote query transform
 */
public class RemoteQueryTransformStepMeta extends SimpleQueryTransformStepMeta
{
    Class targetStepClass = RemoteQueryTransformStep.class;
    String remoteSource;

    public Class getTargetStepClass()
    {
        return targetStepClass;
    }

    public void setTargetStepClass(Class targetStepClass)
    {
        this.targetStepClass = targetStepClass;
    }

    @Override
    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        if (null != transformXML.getSource())
        {
            parseSource(transformXML);
        }
        else throw new XmlException(TransformManager.INVALID_SOURCE);

        if (null != transformXML.getDestination())
        {
            super.parseDestination(transformXML);
        }
        else throw new XmlException(TransformManager.INVALID_DESTINATION);
    }

    protected void parseSource(TransformType transformXML) throws XmlException
    {
        SchemaQueryType source = transformXML.getSource();

        if (null != source)
        {
            setSourceSchema(SchemaKey.fromString(source.getSchemaName()));
            setSourceQuery(source.getQueryName());
            setRemoteSource(source.getRemoteSource()); // for this line, parseSource and parseWorkOptions were overridden
            if (null != source.getTimestampColumnName())
                setSourceTimestampColumnName(source.getTimestampColumnName());
            try
            {
                if (null != source.getSourceOption())
                   setSourceOptions(CopyConfig.SourceOptions.valueOf(source.getSourceOption().toString()));
            }
            catch (XmlValueOutOfRangeException e)
            {
                throw new XmlException(TransformManager.INVALID_SOURCE_OPTION);
            }
        }
    }

    public String getRemoteSource()
    {
        return remoteSource;
    }

    public void setRemoteSource(String remoteSource)
    {
        this.remoteSource = remoteSource;
    }
}
