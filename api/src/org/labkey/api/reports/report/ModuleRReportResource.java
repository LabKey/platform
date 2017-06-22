/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.reports.RScriptEngine;
import org.labkey.api.resource.Resource;
import org.labkey.query.xml.FunctionType;
import org.labkey.query.xml.FunctionsType;
import org.labkey.query.xml.ReportDescriptorType;
import org.labkey.query.xml.ReportType;
import org.labkey.query.xml.ScriptEngineType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * User: dax
 * Date: 6/13/14
 *
 * Knitr R reports can declare client dependencies
 * Rserve R reports can declare functions that are callable from the Report.executeFunction method
 */
public class ModuleRReportResource extends ModuleReportDependenciesResource
{
    private HashSet<String> _functions;

    // Script Engine properties this report requests.  For now, this is only whether the report should run
    // against a remote or local R engine
    private HashMap<String, String> _scriptEngineProperties;

    public ModuleRReportResource(ReportDescriptor reportDescriptor, Resource sourceFile)
    {
        super(reportDescriptor, sourceFile);
    }

    @Override
    protected org.labkey.query.xml.DependenciesType getXmlDependencies(ReportType type) throws XmlException
    {
        if (type.isSetR())
            return type.getR().getDependencies();

        throw new XmlException("Metadata associated with a Report must have a ReportType of R");
    }

    protected org.labkey.query.xml.FunctionsType getXmlFunctions(ReportType type) throws XmlException
    {
        if (type.isSetR())
            return type.getR().getFunctions();

        throw new XmlException("Metadata associated with a Report must have a ReportType of R");
    }

    protected org.labkey.query.xml.ScriptEngineType getXmlScriptEngine(ReportType type) throws XmlException
    {
        if (type.isSetR())
            return type.getR().getScriptEngine();

        throw new XmlException("Metadata associated with a Report must have a ReportType of R");
    }

    @Override
    protected ReportDescriptorType loadMetaData()
    {
        ReportDescriptorType d = super.loadMetaData();

        if (null != d)
        {
            try
            {
                if (d.getReportType() != null)
                {
                    FunctionsType xmlFunctions = getXmlFunctions(d.getReportType());
                    setCallableFunctions(xmlFunctions);

                    ScriptEngineType xmlScriptEngine = getXmlScriptEngine(d.getReportType());
                    setScriptEngineProperties(xmlScriptEngine);
                }
            }
            catch (XmlException e)
            {
                Logger.getLogger(ModuleRReportResource.class).warn("Unable to load R report metadata from file "
                        + _sourceFile.getPath(), e);
            }
        }

        return d;
    }

    private void setCallableFunctions(FunctionsType xmlFunctions)
    {
        _functions = new HashSet<>();
        if (xmlFunctions != null)
        {
            for (FunctionType function : xmlFunctions.getFunctionArray())
            {
                String name = function.getName();
                if (null != name && !name.isEmpty())
                    _functions.add(name);
            }
        }
    }

    //
    // Read the engine properties stored in the metdata that allow a report to advertise what engine it
    // wants to run against.  This enables users to declare both remote and local R engines, for example,
    // and have the report choose which one to pick.  For now, we only have one recognized property:  remote. If not
    // set, we default to false
    //
    private void setScriptEngineProperties(ScriptEngineType xmlScriptEngine)
    {
        _scriptEngineProperties = new HashMap<>();
        if (xmlScriptEngine != null)
        {
            if (xmlScriptEngine.isSetRemote())
                _scriptEngineProperties.put(RScriptEngine.PROP_REMOTE, String.valueOf(xmlScriptEngine.getRemote()));
        }
    }

    public HashSet<String> getCallableFunctions()
    {
        if (_functions == null)
            return new HashSet<>();

        return _functions;
    }

    public Map<String, String> getScriptEngineProperties()
    {
        if (_scriptEngineProperties == null)
            return new HashMap<>();

        return _scriptEngineProperties;
    }
}
