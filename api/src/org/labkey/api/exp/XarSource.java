/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.xmlbeans.XmlException;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Something that knows how to a produce a XAR (experiment archive), whether it's a from an existing file or
 * being dynamically generated on demand.
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

    private final Map<String, ExpSampleType> _xarSampleTypes = new HashMap<>();
    private final Map<String, ExpDataClass> _xarDataClasses = new HashMap<>();

    protected final Map<String, String> _dataFileURLs = new HashMap<>();

    @NotNull
    private final XarContext _xarContext;

    public XarSource(String description, Container container, User user, @Nullable PipelineJob job)
    {
        _xarContext = new XarContext(description, container, user, job);
    }

    public XarSource(String description, Container container, User user, @Nullable PipelineJob job, @Nullable Map<String, String> substitutions)
    {
        _xarContext = new XarContext(description, container, user, job, substitutions);
    }

    public XarSource(PipelineJob job)
    {
        _xarContext = new XarContext(job);
    }

    public abstract ExperimentArchiveDocument getDocument() throws XmlException, IOException;

    public abstract Path getRootPath();

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
                if ("file".equalsIgnoreCase(uri.getScheme()) || FileUtil.hasCloudScheme(uri))
                {
                    urlToLookup = FileUtil.uriToString(uri);
                }
            }
            catch (IllegalArgumentException | URISyntaxException ignored) {}
            result = canonicalizeDataFileURL(urlToLookup);
            _dataFileURLs.put(dataFileURL, result);
            _dataFileURLs.put(urlToLookup, result);
        }
        return result;
    }

    protected abstract String canonicalizeDataFileURL(String dataFileURL) throws XarFormatException;

    public abstract Path getLogFilePath() throws IOException;

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

    public void addData(String experimentRunLSID, ExpData data, @Nullable String additionalDataLSID)
    {
        Map<String, ExpData> map = _data.computeIfAbsent(experimentRunLSID, k -> new HashMap<>());
        map.put(data.getLSID(), data);
        if (additionalDataLSID != null)
        {
            map.put(additionalDataLSID, data);
        }
    }

    public void addMaterial(String experimentRunLSID, ExpMaterial material, @Nullable String additionalMaterialLSID)
    {
        Map<String, ExpMaterial> map = _materials.computeIfAbsent(experimentRunLSID, k -> new HashMap<>());
        map.put(material.getLSID(), material);
        if (additionalMaterialLSID != null)
        {
            map.put(additionalMaterialLSID, material);
        }
    }

    public ExpData getData(ExpRun experimentRun, ExpProtocolApplication protApp, String dataLSID) throws XarFormatException
    {
        String experimentRunLSID = experimentRun == null ? null : experimentRun.getLSID();
        Map<String, ExpData> map = _data.computeIfAbsent(experimentRunLSID, k -> new HashMap<>());
        ExpData result = map.get(dataLSID);
        if (result == null)
        {
            if (experimentRun == null)
            {
                result = ExperimentService.get().getExpData(dataLSID);
            }
            if (result == null)
            {
                // Try for a non-run scoped variant
                result = _data.computeIfAbsent(null, k -> new HashMap<>()).get(dataLSID);
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

    private ExpMaterial getMaterialOLD(ExpRun experimentRun, ExpProtocolApplication protApp, String materialLSID) throws XarFormatException
    {
        String experimentRunLSID = experimentRun == null ? null : experimentRun.getLSID();
        Map<String, ExpMaterial> map = _materials.computeIfAbsent(experimentRunLSID, k -> new HashMap<>());
        ExpMaterial result = map.get(materialLSID);
        if (result == null)
        {
            if (experimentRun == null)
            {
                result = ExperimentService.get().getExpMaterial(materialLSID);
            }
            if (result == null)
            {
                // Try for a non-run scoped variant
                result = _materials.computeIfAbsent(null, k -> new HashMap<>()).get(materialLSID);
            }
            if (result == null)
            {
                throw new XarFormatException(createIllegalReferenceMessage(experimentRun, protApp, materialLSID, "Material"));
            }
            map.put(result.getLSID(), result);
        }
        return result;
    }

    public ExpMaterial getMaterial(ExpRun experimentRun, ExpProtocolApplication protApp, String materialLSID) throws XarFormatException
    {
        String experimentRunLSID = experimentRun == null ? null : experimentRun.getLSID();
        Map<String, ExpMaterial> map = _materials.computeIfAbsent(experimentRunLSID, k -> new HashMap<>());
        ExpMaterial result = map.get(materialLSID);
        if (result == null)
        {
            // Try for a non-run scoped variant
            result = _materials.computeIfAbsent(null, k -> new HashMap<>()).get(materialLSID);
            if (null == result)
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

    public boolean allowImport(PipeRoot pr, Container container, Path path)
    {
        try
        {
            return (pr != null && pr.isUnderRoot(path)) ||
                    (!FileUtil.pathToString(path).equalsIgnoreCase(FileUtil.relativizeUnix(getRootPath(), path, true)));
        }
        catch (IOException e)
        {
            return false;
        }
    }

    @NotNull
    public XarContext getXarContext()
    {
        return _xarContext;
    }

    public void addSampleType(String sampleTypeLSID, ExpSampleType sampleType)
    {
        _xarSampleTypes.put(sampleTypeLSID, sampleType);
    }

    public ExpSampleType getSampleType(String sampleTypeLSID)
    {
        return _xarSampleTypes.get(sampleTypeLSID);
    }

    public void addDataClass(String sampleTypeLSID, ExpDataClass dataClass)
    {
        _xarDataClasses.put(sampleTypeLSID, dataClass);
    }

    public ExpDataClass getDataClass(String sampleTypeLSID)
    {
        return _xarDataClasses.get(sampleTypeLSID);
    }

}
