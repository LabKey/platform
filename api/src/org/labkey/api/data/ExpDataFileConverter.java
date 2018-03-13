/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.converters.FileConverter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Path;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;

/**
 * User: jeckels
 * Date: Sep 30, 2011
 */
public class ExpDataFileConverter implements Converter
{
    private static final Converter FILE_CONVERTER = new FileConverter();

    public static ExpData resolveExpData(JSONObject dataObject, Container container, User user, @NotNull Collection<AssayDataType> knownTypes)
    {
        ExperimentService expSvc = ExperimentService.get();

        PipeRoot pipelineRoot = container == null ? null : PipelineService.get().getPipelineRootSetting(container);

        // First look it up by rowId
        if (dataObject.has(ExperimentJSONConverter.ID))
        {
            int dataId = dataObject.getInt(ExperimentJSONConverter.ID);
            ExpData data = expSvc.getExpData(dataId);

            if (data == null)
            {
                throw new NotFoundException("Could not find data with id " + dataId);
            }
            if (container != null && !data.getContainer().equals(container))
            {
                throw new NotFoundException("Data with row id " + dataId + " is not in folder " + container);
            }
            return data;
        }
        // Then try as an LSID
        else if (dataObject.has(ExperimentJSONConverter.LSID) && dataObject.getString(ExperimentJSONConverter.LSID) != null)
        {
            String lsid = dataObject.getString(ExperimentJSONConverter.LSID);
            ExpData data = expSvc.getExpData(lsid);

            if (data == null)
            {
                throw new NotFoundException("Could not find data with LSID " + lsid);
            }
            if (container != null && !data.getContainer().equals(container))
            {
                throw new NotFoundException("Data with LSID " + lsid + " is not in folder " + container);
            }
            return data;
        }
        // Then try as a relative path from the pipeline root
        else if (dataObject.has(ExperimentJSONConverter.PIPELINE_PATH) && dataObject.getString(ExperimentJSONConverter.PIPELINE_PATH) != null && pipelineRoot != null)
        {
            String pipelinePath = dataObject.getString(ExperimentJSONConverter.PIPELINE_PATH);

            //check to see if this is already an ExpData
            File file = pipelineRoot.resolvePath(pipelinePath);
            ExpData data = null != file ? expSvc.getExpDataByURL(file, container) : null;

            if (null == data)
            {
                if (null == file || !NetworkDrive.exists(file))
                {
                    throw new IllegalArgumentException("No file with relative pipeline path '" + pipelinePath + "' was found");
                }

                //create a new one
                String name = dataObject.optString(ExperimentJSONConverter.NAME, pipelinePath);
                DataType type = getDataType(file, knownTypes);
                String lsid = expSvc.generateLSID(container, type, name);
                data = expSvc.createData(container, name, lsid);
                data.setDataFileURI(file.toPath().toUri());
                data.save(user);
            }
            return data;
        }
        // Try as a full URL on the server's file system - "file://"...
        else if (dataObject.has(ExperimentJSONConverter.DATA_FILE_URL) && dataObject.getString(ExperimentJSONConverter.DATA_FILE_URL) != null)
        {
            String dataFileURL = dataObject.getString(ExperimentJSONConverter.DATA_FILE_URL);
            //check to see if this is already an ExpData
            ExpData data = expSvc.getExpDataByURL(dataFileURL, container);

            if (null == data)
            {
                throw new IllegalArgumentException("Could not find a file for dataFileURL " + dataFileURL);
            }
            return data;
        }
        // Try as a full path on the server's file system - "C:/assaydata/myfile.tsv" or "/assaydata/myfile.tsv", etc
        else if (dataObject.has(ExperimentJSONConverter.ABSOLUTE_PATH) && dataObject.get(ExperimentJSONConverter.ABSOLUTE_PATH) != null)
        {
            String absolutePath = dataObject.getString(ExperimentJSONConverter.ABSOLUTE_PATH);
            File f = new File(absolutePath);
            if (container != null && pipelineRoot != null && !pipelineRoot.isUnderRoot(f))
            {
                throw new IllegalArgumentException("File with path " + absolutePath + " is not under the pipeline root for this folder");
            }
            //check to see if this is already an ExpData
            ExpData data = expSvc.getExpDataByURL(f, container);

            if (null == data && container != null)
            {
                f = FileUtil.getAbsoluteCaseSensitiveFile(f);
                String name = dataObject.optString(ExperimentJSONConverter.NAME, f.getName());
                DataType type = getDataType(f, knownTypes);
                String lsid = expSvc.generateLSID(container, type, name);
                data = expSvc.createData(container, name, lsid);
                data.setDataFileURI(f.toURI());
                data.save(user);
            }
            return data;
        }
        else
            throw new IllegalArgumentException("Data must have an id, LSID, pipelinePath, dataFileURL, or absolutePath property. " + dataObject);
    }

    /** Check for the first matching data type, defaulting to RELATED_FILE_DATA_TYPE if none match */
    private static DataType getDataType(@NotNull File file, @NotNull Collection<AssayDataType> knownTypes)
    {
        for (AssayDataType knownType : knownTypes)
        {
            if (knownType.getFileType().isType(file))
            {
                return knownType;
            }
        }
        return AbstractAssayProvider.RELATED_FILE_DATA_TYPE;
    }

    @Override
    public Object convert(Class type, Object value)
    {
        if (value == null)
        {
            return null;
        }

        if (type.isAssignableFrom(File.class))
        {
            if (value instanceof File)
            {
                return value;
            }

            if (value instanceof JSONObject)
            {
                // Assume the same structure as the saveBatch and getBatch APIs work with
                ExpData data = resolveExpData((JSONObject)value, null, null, Collections.emptyList());
                if (data != null && data.getFile() != null)
                {
                    return data.getFile();
                }
            }

            // Value specified as simple property, so we have to guess what it might be
            // First, try looking it up as a RowId
            try
            {
                int dataRowId = Integer.parseInt(value.toString());
                ExpData data = ExperimentService.get().getExpData(dataRowId);
                if (data != null)
                {
                    File result = data.getFile();
                    if (result != null)
                    {
                        return result;
                    }
                }
            }
            catch (NumberFormatException ignored)
            {
            }

            // toss in here an additional check, if starts with HTTP then try to use _webdav to resolve it
            // MAKE sure that the security is in place - figure out what container it is in
            String webdav = value.toString();
            if (null != StringUtils.trimToNull(webdav))
            {
                Path path = Path.decode(webdav.replace(AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath(), ""));
                WebdavResource resource = WebdavService.get().getResolver().lookup(path);
                if (resource != null && resource.isFile())
                {
                    File result = resource.getFile();
                    if (result != null)
                    {
                        return result;
                    }
                }
                else
                {
                    if (path.isDirectory())
                    {
                        try
                        {
                            resource = WebdavService.get().lookupHref(path.toString());
                            if (null != resource && !resource.isFile())
                                return resource.getFile();
                        }
                        catch(URISyntaxException ignored)
                        {
                        }
                    }
                }
            }


            // Then, see if we can find it as an LSID
            String lsid = value.toString();
            ExpData data = ExperimentService.get().getExpData(lsid);
            if (data != null)
            {
                File result = data.getFile();
                if (result != null)
                {
                    return result;
                }
            }
        }

        // Otherwise, treat it as a plain path
        return FILE_CONVERTER.convert(type, value);
    }
}
