/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:43:07 PM
 */
public class StudyExportContext extends AbstractContext
{
    private final boolean _oldFormats;
    private final Set<String> _dataTypes;
    private final List<DataSetDefinition> _datasets = new LinkedList<DataSetDefinition>();
    private final Set<Integer> _datasetIds = new HashSet<Integer>();

    private boolean _locked = false;

    public StudyExportContext(StudyImpl study, User user, Container c, boolean oldFormats, Set<String> dataTypes, Logger logger)
    {
        super(user, c, StudyXmlWriter.getStudyDocument(), logger);
        _oldFormats = oldFormats;
        _dataTypes = dataTypes;
        initializeDatasets(study);
    }

    public File getStudyDir(File root, String dirName) throws StudyImportException
    {
        throw new IllegalStateException("Not supported during export");
    }

    public void lockStudyDocument()
    {
        _locked = true;
    }

    @Override
    // Full study doc -- only interesting to StudyXmlWriter
    public StudyDocument getStudyDocument() throws StudyImportException
    {
        if (_locked)
            throw new IllegalStateException("Can't access StudyDocument after study.xml has been written");

        return super.getStudyDocument();
    }

    public boolean useOldFormats()
    {
        return _oldFormats;
    }

    public Set<String> getDataTypes()
    {
        return _dataTypes;
    }

    private void initializeDatasets(StudyImpl study)
    {
        boolean includeCRF = _dataTypes.contains(DatasetWriter.SELECTION_TEXT);
        boolean includeAssay = _dataTypes.contains(AssayDatasetWriter.SELECTION_TEXT);

        for (DataSetDefinition dataset : study.getDataSets())
        {
            if ((null == dataset.getProtocolId() && includeCRF) || (null != dataset.getProtocolId() && includeAssay))
            {
                _datasets.add(dataset);
                _datasetIds.add(dataset.getDataSetId());
            }
        }
    }

    public boolean isExportedDataset(Integer datasetId)
    {
        return _datasetIds.contains(datasetId);
    }

    public List<DataSetDefinition> getDatasets()
    {
        return _datasets;
    }
}
