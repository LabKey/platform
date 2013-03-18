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
package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Path;
import org.labkey.etl.xml.EtlDocument;
import org.labkey.etl.xml.EtlType;
import org.quartz.ScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class ETLDescriptor implements Serializable
{
    private static final Logger LOG = Logger.getLogger(ETLDescriptor.class);

    /** How often to check if the definition has changed */
    private static final int UPDATE_CHECK_FREQUENCY = 2000;

    private transient Resource _resource;
    private Path _resourcePath;
    private long _lastUpdateCheck;
    private long _lastModified;

    private String _name;
    private String _description;
    private SchemaKey _sourceSchema;
    private String _sourceQuery;
    private SchemaKey _destinationSchema;
    private String _destinationQuery;
    private String _moduleName;

    public ETLDescriptor(Resource resource, String moduleName) throws XmlException, IOException
    {
        _resource = resource;
        _resourcePath = resource.getPath();
        _moduleName = moduleName;
        parse();
    }

    private void parse() throws IOException, XmlException
    {
        InputStream inputStream = null;
        try
        {
            Resource resource = ensureResource();
            inputStream = resource.getInputStream();
            if (inputStream == null)
            {
                throw new IOException("Unable to get InputStream from " + resource);
            }
            _lastModified = resource.getLastModified();

            XmlOptions options = new XmlOptions();
            options.setValidateStrict();
            EtlDocument document = EtlDocument.Factory.parse(inputStream, options);
            EtlType etlXML = document.getEtl();

            _name = etlXML.getName();
            _sourceSchema = SchemaKey.fromString(etlXML.getSource().getSchemaName());
            _sourceQuery = etlXML.getSource().getQueryName();
            _destinationSchema = SchemaKey.fromString(etlXML.getDestination().getSchemaName());
            _destinationQuery = etlXML.getDestination().getQueryName();
        }
        finally
        {
            if (inputStream != null) { try { inputStream.close(); } catch (IOException ignored) {} }
        }

    }

    private Resource ensureResource()
    {
        if (_resource == null)
        {
            _resource = ModuleLoader.getInstance().getResource(_resourcePath);
            if (_resource == null)
            {
                throw new IllegalStateException("Could not resolve resource for " + _resourcePath + ", perhaps the ETL descriptor is no longer available?");
            }
        }
        return _resource;
    }

    public String getName()
    {
        checkForUpdates();
        return _name;
    }

    public String getDescription()
    {
        checkForUpdates();
        return _description;
    }

    public String getModuleName()
    {
        return _moduleName;
    }

    public String getTransformId()
    {
        return _resourcePath.toString();
    }

    public int getTransformVersion()
    {
        checkForUpdates();
        // TODO - add config for real version number
        return 1;
    }

    public SchemaKey getSourceSchema()
    {
        checkForUpdates();
        return _sourceSchema;
    }

    public String getSourceQuery()
    {
        checkForUpdates();
        return _sourceQuery;
    }

    public SchemaKey getDestinationSchema()
    {
        checkForUpdates();
        return _destinationSchema;
    }

    public String getDestinationQuery()
    {
        checkForUpdates();
        return _destinationQuery;
    }

    private void checkForUpdates()
    {
        long currentTime = System.currentTimeMillis();
        if (_lastUpdateCheck + UPDATE_CHECK_FREQUENCY < currentTime)
        {
            _lastUpdateCheck = currentTime;
            if (_lastModified != ensureResource().getLastModified())
            {
                // XML has changed, time to reload
                try
                {
                    parse();
                }
                catch (IOException e)
                {
                    LOG.warn("Unable to parse " + ensureResource(), e);
                }
                catch (XmlException e)
                {
                    LOG.warn("Unable to parse " + ensureResource(), e);
                }
            }
        }
    }

    @Override
    public String toString()
    {
        return "ETLDescriptor: " + _name + " (" + getScheduleDescription() + ")";
    }


    public ScheduleBuilder getScheduleBuilder()
    {
        return SimpleScheduleBuilder.simpleSchedule()
                              .withIntervalInMilliseconds(getInterval())
                              .repeatForever();
    }


    long getInterval()
    {
        return TimeUnit.MINUTES.toMillis(1);
    }


    public String getScheduleDescription()
    {
        return DateUtil.formatDuration(getInterval());
    }


    public Map<String, Object> toJSON(@Nullable Map<String,Object> map)
    {
        if (null == map)
            map = new JSONObject();
        map.put("transformId", getTransformId());
        map.put("description", getDescription());
        map.put("name", getName());
        map.put("moduleName", getModuleName());
        map.put("scheduleDescription", getScheduleDescription());
        map.put("transformVersion", getTransformVersion());
        return map;
    }
}
