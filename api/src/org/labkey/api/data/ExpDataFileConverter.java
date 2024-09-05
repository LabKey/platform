/*
 * Copyright (c) 2011-2018 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayDataType;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Path;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;

import static org.labkey.api.dataiterator.SimpleTranslator.getFileRootSubstitutedFilePath;

/**
 * User: jeckels
 * Date: Sep 30, 2011
 */
public class ExpDataFileConverter implements Converter
{
    private static final Converter FILE_CONVERTER = new FileConverter();

    public static ExpData resolveExpData(JSONObject dataObject, @NotNull Container container, @NotNull User user, @NotNull Collection<AssayDataType> knownTypes)
    {
        ExperimentService expSvc = ExperimentService.get();

        PipeRoot pipelineRoot = PipelineService.get().getPipelineRootSetting(container);

        // First look it up by rowId
        if (dataObject.has(ExperimentJSONConverter.ID))
        {
            int dataId = dataObject.getInt(ExperimentJSONConverter.ID);
            ExpData data = expSvc.getExpData(dataId);

            if (data == null)
            {
                throw new NotFoundException("Could not find data with id " + dataId);
            }
            if (!data.getContainer().equals(container))
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
            if (!data.getContainer().equals(container))
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
                if (!NetworkDrive.exists(file))
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
        // Try as a full URL on the server's file system - "file://"... or path to the file relative to the file root
        else if (dataObject.has(ExperimentJSONConverter.DATA_FILE_URL) && dataObject.getString(ExperimentJSONConverter.DATA_FILE_URL) != null)
        {
            String dataFileURL = dataObject.getString(ExperimentJSONConverter.DATA_FILE_URL);
            //check to see if this is already an ExpData
            ExpData data = expSvc.getExpDataByURL(dataFileURL, container);

            FileContentService fileContent = FileContentService.get();
            if (null == data && fileContent != null)
            {
                // Check for file at file root
                URI dataFileUri = fileContent.getFileRootUri(container, FileContentService.ContentType.files, dataFileURL);
                if (dataFileUri == null) {
                    throw new IllegalArgumentException("Could not resolve file at file root: " + dataFileURL);
                }

                data = expSvc.getExpDataByURL(dataFileUri.toString(), container);

                if (null == data)
                {
                    throw new IllegalArgumentException("Could not find a file for dataFileURL " + dataFileURL);
                }
            }
            return data;
        }
        // Try as a full path on the server's file system - "C:/assaydata/myfile.tsv" or "/assaydata/myfile.tsv", etc
        else if (dataObject.has(ExperimentJSONConverter.ABSOLUTE_PATH) && dataObject.get(ExperimentJSONConverter.ABSOLUTE_PATH) != null)
        {
            String absolutePath = dataObject.getString(ExperimentJSONConverter.ABSOLUTE_PATH);
            File f = FileUtil.getAbsoluteCaseSensitiveFile(new File(absolutePath));
            if (pipelineRoot != null && !pipelineRoot.isUnderRoot(f))
            {
                throw new IllegalArgumentException("File with path " + absolutePath + " is not under the pipeline root for this folder");
            }
            //check to see if this is already an ExpData
            ExpData data = expSvc.getExpDataByURL(f, container);

            if (null == data)
            {
                String name = dataObject.optString(ExperimentJSONConverter.NAME, f.getName());
                DataType type = getDataType(f, knownTypes);
                String lsid = expSvc.generateLSID(container, type, name);
                data = expSvc.createData(container, name, lsid);
                data.setDataFileURI(f.toURI());
                data.save(user);
            }
            return data;
        }
        // try to resolve by data class properties
        else if (dataObject.has(ExperimentJSONConverter.DATA_CLASS) && dataObject.has(ExperimentJSONConverter.NAME))
        {
            JSONObject dataClass = dataObject.getJSONObject(ExperimentJSONConverter.DATA_CLASS);
            ExpDataClass expDataClass = null;
            if (dataClass.has(ExperimentJSONConverter.ID))
                expDataClass = ExperimentService.get().getDataClass(container, dataClass.getInt(ExperimentJSONConverter.ID));
            if (dataClass.has(ExperimentJSONConverter.NAME))
                expDataClass = ExperimentService.get().getDataClass(container, dataClass.getString(ExperimentJSONConverter.NAME));

            if (expDataClass != null)
            {
                ExpData data = expDataClass.getData(container, dataObject.getString(ExperimentJSONConverter.NAME));
                if (data != null)
                    return data;
            }
            throw new IllegalArgumentException("Could not resolve a dataclass data object from the specified parameters");
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

        Container container = (Container) QueryService.get().getEnvironment(QueryService.Environment.CONTAINER);
        User user = (User) QueryService.get().getEnvironment(QueryService.Environment.USER);
        Object fileRootPathObj = QueryService.get().getEnvironment(QueryService.Environment.FILEROOTPATH);
        String fileRootPath = fileRootPathObj == null ? null : (String) fileRootPathObj;

        // Don't bother resolving if we don't know the container, or we don't know the user has permission to the container
        if (type.isAssignableFrom(File.class) && container != null && user != null && container.hasPermission(user, ReadPermission.class))
        {
            File f = convertToFile(value, container, user, fileRootPath);

            // If we have a file path, make sure it's supposed to be visible in the current container
            if (f != null)
            {
                // Strip out ".." and "."
                f = FileUtil.resolveFile(f);

                PipeRoot root = PipelineService.get().getPipelineRootSetting(container);
                if (root != null)
                {
                    if (!f.isAbsolute())
                    {
                        // Interpret relative paths based on the file root
                        f = new File(root.getRootPath(), f.getPath());
                    }

                    if (root.isUnderRoot(f))
                    {
                        return f;
                    }
                }

                // It's possible to have the file root and pipeline root pointed at different paths
                FileContentService fileContent = FileContentService.get();
                if (fileContent != null)
                {
                    File fileRoot = fileContent.getFileRoot(container);
                    if (fileRoot != null && URIUtil.isDescendant(fileRoot.toURI(), f.toURI()))
                    {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    private File convertToFile(Object value, @NotNull Container container, @NotNull User user, @Nullable String fileRootPath)
    {
        if (value instanceof File f)
        {
            return f;
        }

        if (value instanceof JSONObject json)
        {
            // Assume the same structure as the saveBatch and getBatch APIs work with
            ExpData data = resolveExpData(json, container, user, Collections.emptyList());
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
            webdav = getFileRootSubstitutedFilePath(webdav, fileRootPath);
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
        // Otherwise, treat it as a plain path
        return FILE_CONVERTER.convert(File.class, webdav);
    }
}
