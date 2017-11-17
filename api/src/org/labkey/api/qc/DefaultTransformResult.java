/*
 * Copyright (c) 2009-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.qc;

import org.apache.log4j.Logger;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.property.DomainProperty;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: May 7, 2009
 */
public class DefaultTransformResult implements TransformResult
{
    private Map<ExpData, List<Map<String, Object>>> _dataMap = Collections.emptyMap();
    private Map<DomainProperty, String> _batchProperties = Collections.emptyMap();
    private Map<DomainProperty, String> _runProperties = Collections.emptyMap();
    private File _uploadedFile;
    private String _assayId;
    private String _comments;
    private String _warnings;
    private List<File> _files;
    private static final Logger LOG = Logger.getLogger(DefaultTransformResult.class);

    public List<File> getFiles()
    {
        return _files;
    }

    public void setFiles(List<File> files)
    {
        _files = files;
    }

    public String getWarnings()
    {
        return _warnings;
    }

    public void setWarnings(String warnings)
    {
        _warnings = warnings;
    }

    public DefaultTransformResult(){}

    public DefaultTransformResult(Map<ExpData, List<Map<String, Object>>> dataMap)
    {
        _dataMap = dataMap;
        _assayId = null;
    }

    public DefaultTransformResult(TransformResult mergeResult)
    {
        _dataMap = mergeResult.getTransformedData();
        _batchProperties = mergeResult.getBatchProperties();
        _runProperties = mergeResult.getRunProperties();
        _uploadedFile = mergeResult.getUploadedFile();
        _warnings = mergeResult.getWarnings();
        _files = mergeResult.getFiles();
        _assayId = null;
    }

    public Map<ExpData, List<Map<String, Object>>> getTransformedData()
    {
        return _dataMap;
    }

    public Map<DomainProperty, String> getBatchProperties()
    {
        return _batchProperties;
    }

    public void setBatchProperties(Map<DomainProperty, String> batchProperties)
    {
        _batchProperties = batchProperties;
    }

    public Map<DomainProperty, String> getRunProperties()
    {
        return _runProperties;
    }

    public void setRunProperties(Map<DomainProperty, String> runProperties)
    {
        _runProperties = runProperties;
    }

    public File getUploadedFile()
    {
        return _uploadedFile;
    }

    public void setUploadedFile(File uploadedFile)
    {
        _uploadedFile = uploadedFile;
    }

    public static TransformResult createEmptyResult()
    {
        return new DefaultTransformResult();
/*
        return new TransformResult()
        {
            public Map<DataType, File> getTransformedData()
            {
                return Collections.emptyMap();
            }
            public boolean isEmpty() {return true;}
        };
*/
    }

    public String getAssayId()
    {
        return _assayId;
    }

    public void setAssayId(String assayId)
    {
        _assayId = assayId;
    }

    @Override
    public String getComments()
    {
        return _comments;
    }

    @Override
    public void setComments(String comments)
    {
        _comments = comments;
    }
}
