/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.xarassay ;

import org.labkey.api.action.FormArrayList;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.study.actions.AssayRunUploadForm;

import java.io.File;
import java.net.URI;
import java.util.*;

/**
 * User: phussey
 * Date: Sep 17, 2007
 * Time: 12:30:55 AM
 */
public class XarAssayForm extends AssayRunUploadForm
{
    private URI _dataURI;
    private String _currentFileName;
    private ExpProtocol selectedProtocol;
    private String _sampleSetUrl;
    private String _path;
    private Integer _numFilesRemaining;
    private ArrayList<String> _undescribedFileNames = new FormArrayList<String>(String.class) ;
    private Map<String, String> _links = new LinkedHashMap<String, String>();

    //private int _dataId;

    public XarAssayForm()
    {
        super();
        setDataCollectorName(XarAssayDataCollector.NAME);
    }

    @Override
    public void clearUploadedData()
    {
        // don't clear the data, but do reset the check for undescribed files
        _numFilesRemaining=null;
        _currentFileName=null;
    }

    public String getPath()
    {
        return _path;
    }

    public void setPath(String path)
    {
        _path = path;
    }

    public ExpProtocol getSelectedProtocol()
    {
        return selectedProtocol;
    }

    public void setSelectedProtocol(ExpProtocol selectedProtocol)
    {
        this.selectedProtocol = selectedProtocol;
    }

    public String getSampleSetUrl()
    {
        return _sampleSetUrl;
    }

    public void setSampleSetUrl(String sampleSetUrl)
    {
        _sampleSetUrl = sampleSetUrl;
    }

     public URI getDataURI()
    {
        return _dataURI;
    }

    public void setDataURI(URI dataURI)
    {
        _dataURI = dataURI;
    }

    public Integer getNumFilesRemaining()
    {
        if (null== _numFilesRemaining)
            getUndescribedFiles();  
        return _numFilesRemaining;
    }

    public String getCurrentFileName()
    {
        return _currentFileName;
    }
    
    public ArrayList<String> getUndescribedFiles()
    {
        if (null== _numFilesRemaining)
        {
            ArrayList<String> udf = new ArrayList<String>();
            ExpData d = null;
            File f;
            SortedMap<String, File> sm;

            try
            {
                sm = new TreeMap<String, File>(getUploadedData());
                for (Map.Entry<String, File> entry : sm.entrySet())
                {
                    f = entry.getValue();

                    d = ExperimentService.get().getDataByURL(f, getContainer());
                    if ((null == d) || (null== d.getRun()))
                        udf.add(entry.getKey());
                }
                _undescribedFileNames = udf;
                _numFilesRemaining = udf.size();
                if (_numFilesRemaining > 0)
                    _currentFileName = udf.get(0);
                else
                    _currentFileName = null;

            }
            catch (Exception e)
            {
                throw new RuntimeException((e));
            }
        }
        return _undescribedFileNames;

    }

    public Map<String, String> getLinks()
    {
        return _links;
    }

    public void setLinks(Map<String, String> links)
    {
        _links = links;
    }

    //todo: can I switch to built-in sample handling?
    public Map<String, String> getRunSamplesByCaption()
    {
        return Collections.emptyMap();
    }

    public Map<String, String> getRunSamplesByFormElementName()
    {
        return Collections.emptyMap();
    }

    public Collection<String> getSampleIds()
    {
        return Collections.emptyList();
    }
}
