/*
 * Copyright (c) 2005-2013 LabKey Corporation
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
package org.labkey.api.exp;

import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.apache.xmlbeans.XmlException;

import java.io.IOException;
import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * User: jeckels
 * Date: Oct 14, 2005
 */
public abstract class XarSource implements Serializable
{
    public static final String LOG_FILE_NAME_SUFFIX = ".log";

    private Integer _experimentRunId;
    private final Map<String, ExpProtocol> _xarProtocols = new HashMap<>();
    private final Map<String, ExpProtocol> _databaseProtocols = new HashMap<>();
    private final Map<String, Map<String, ExpMaterial>> _materials = new HashMap<>();
    private final Map<String, Map<String, ExpData>> _data = new HashMap<>();

    protected final Map<String, String> _dataFileURLs = new HashMap<>();

    private final XarContext _xarContext;

    public XarSource(String description, Container container, User user)
    {
        _xarContext = new XarContext(description, container, user);
    }

    public XarSource(PipelineJob job)
    {
        _xarContext = new XarContext(job);
    }

    public abstract ExperimentArchiveDocument getDocument() throws XmlException, IOException;

    public abstract File getRoot();

    /**
     * Should be true if this was uploaded XML that was not part of a full XAR
     */
    public abstract boolean shouldIgnoreDataFiles();

    /**
     * Transforms the dataFileURL, which may be relative, to a canonical, absolute URI
     */
    public final String getCanonicalDataFileURL(String dataFileURL) throws XarFormatException
    {
        if (dataFileURL == null)
        {
            return null;
        }
        String result = _dataFileURLs.get(dataFileURL);
        if (result == null)
        {
            String urlToLookup = dataFileURL;
            try
            {
                URI uri = new URI(dataFileURL);
                if ("file".equalsIgnoreCase(uri.getScheme()))
                {
                    urlToLookup = new File(uri).getPath();
                }
            }
            catch (IllegalArgumentException ignored) {}
            catch (URISyntaxException ignored) {}
            result = canonicalizeDataFileURL(urlToLookup);
            _dataFileURLs.put(dataFileURL, result);
            _dataFileURLs.put(urlToLookup, result);
        }
        return result;
    }

    protected abstract String canonicalizeDataFileURL(String dataFileURL) throws XarFormatException;

    public abstract File getLogFile() throws IOException;

    /**
     * Called before trying to import this XAR to let the source set up any resources that are required 
     */
    public void init() throws IOException, ExperimentException
    {
    }

    public void setExperimentRunRowId(Integer experimentRowId)
    {
        _experimentRunId = experimentRowId;
    }

    public ExpRun getExperimentRun()
    {
        if (_experimentRunId != null)
        {
            return ExperimentService.get().getExpRun(_experimentRunId.intValue());
        }
        return null;
    }

    public void addData(String experimentRunLSID, ExpData data)
    {
        Map<String, ExpData> existingMap = _data.get(experimentRunLSID);
        if (existingMap == null)
        {
            existingMap = new HashMap<>();
            _data.put(experimentRunLSID, existingMap);
        }
        existingMap.put(data.getLSID(), data);
    }

    public void addMaterial(String experimentRunLSID, ExpMaterial material)
    {
        Map<String, ExpMaterial> existingMap = _materials.get(experimentRunLSID);
        if (existingMap == null)
        {
            existingMap = new HashMap<>();
            _materials.put(experimentRunLSID, existingMap);
        }
        existingMap.put(material.getLSID(), material);
    }

    public ExpData getData(ExpRun experimentRun, ExpProtocolApplication protApp, String dataLSID) throws XarFormatException
    {
        String experimentRunLSID = experimentRun == null ? null : experimentRun.getLSID();
        Map<String, ExpData> map = _data.get(experimentRunLSID);
        if (map == null)
        {
            map = new HashMap<>();
            _data.put(experimentRunLSID, map);
        }
        ExpData result = map.get(dataLSID);
        if (result == null)
        {
            if (experimentRun == null)
            {
                result = ExperimentService.get().getExpData(dataLSID);
            }
            if (result == null)
            {
                throw new XarFormatException(createIllegalReferenceMessage(experimentRun, protApp, dataLSID, ExpData.DEFAULT_CPAS_TYPE));
            }
            map.put(result.getLSID(), result);
        }
        return result;
    }

    private String createIllegalReferenceMessage(ExpRun experimentRun, ExpProtocolApplication protApp, String lsid, String type)
    {
        String message = "Illegal reference to " + type + " '" + lsid + "'";
        if (protApp != null)
        {
            message += " from ProtocolApplication '" + protApp.getLSID() + "'";
        }
        if (experimentRun != null)
        {
            message += " in ExperimentRun '" + experimentRun.getLSID() + "'";
        }
        return message;
    }

    public ExpMaterial getMaterial(ExpRun experimentRun, ExpProtocolApplication protApp, String materialLSID) throws XarFormatException
    {
        String experimentRunLSID = experimentRun == null ? null : experimentRun.getLSID();
        Map<String, ExpMaterial> map = _materials.get(experimentRunLSID);
        if (map == null)
        {
            map = new HashMap<>();
            _materials.put(experimentRunLSID, map);
        }
        ExpMaterial result = map.get(materialLSID);
        if (result == null)
        {
            if (experimentRun == null)
            {
                result = ExperimentService.get().getExpMaterial(materialLSID);
            }
            if (result == null)
            {
                throw new XarFormatException(createIllegalReferenceMessage(experimentRun, protApp, materialLSID, "Material"));
            }
            map.put(result.getLSID(), result);
        }
        return result;
    }

    public void addProtocol(ExpProtocol protocol)
    {
        _xarProtocols.put(protocol.getLSID(), protocol);
    }

    public ExpProtocol getProtocol(String lsid, String errorDescription) throws XarFormatException
    {
        ExpProtocol result = _xarProtocols.get(lsid);
        if (result == null)
        {
            result = _databaseProtocols.get(lsid);
        }
        if (result == null)
        {
            result = ExperimentService.get().getExpProtocol(lsid);
            _databaseProtocols.put(lsid, result);
        }
        if (result == null)
        {
            throw new XarFormatException("Could not find " + errorDescription + " protocol with LSID " + lsid);
        }
        return result;
    }

    public boolean allowImport(PipeRoot pr, Container container, File file)
    {
        return pr != null && pr.isUnderRoot(file);
    }

    public XarContext getXarContext()
    {
        return _xarContext;
    }
}
