/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URLHelper;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpDataImpl extends AbstractProtocolOutputImpl<Data> implements ExpData
{

    /**
     * Temporary mapping until experiment.xml contains the mime type
     */
    private static MimeMap MIME_MAP = new MimeMap();

    static public List<ExpDataImpl> fromDatas(List<Data> datas)
    {
        List<ExpDataImpl> ret = new ArrayList<>(datas.size());
        for (Data data : datas)
        {
            ret.add(new ExpDataImpl(data));
        }
        return ret;
    }

    public ExpDataImpl(Data data)
    {
        super(data);
    }

    @Nullable
    @Override
    public String getDescription()
    {
        return _object.getDescription();
    }

    @Nullable
    public URLHelper detailsURL()
    {
        DataType dataType = getDataType();
        if (dataType == null)
            return null;

        return dataType.getDetailsURL(this);
    }

    public List<ExpProtocolApplicationImpl> getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter(FieldKey.fromParts("DataId"), getRowId()), ExperimentServiceImpl.get().getTinfoDataInput());
    }

    public List<ExpRunImpl> getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoDataInput(), "DataId");
    }

    public DataType getDataType()
    {
        return ExperimentService.get().getDataType(getLSIDNamespacePrefix());
    }

    public void setDataFileURI(URI uri)
    {
        ensureUnlocked();
        if (uri != null && !uri.isAbsolute())
        {
            throw new IllegalArgumentException("URI must be absolute.");
        }
        String s = uri == null ? null : uri.toString();
        // Strip off any trailing "/"
        if (s != null && s.endsWith("/"))
        {
            s = s.substring(0, s.length() - 1);
        }
        _object.setDataFileUrl(s);
    }

    public void save(User user)
    {
        boolean isNew = getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoData());

        if (isNew)
        {
            ExpDataClassImpl dataClass = getDataClass();
            if (dataClass != null)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("lsid", getLSID());
                Table.insert(user, dataClass.getTinfo(), map);
            }
        }
    }

    public URI getDataFileURI()
    {
        String url = _object.getDataFileUrl();
        if (url == null)
            return null;
        try
        {
            return new URI(_object.getDataFileUrl());
        }
        catch (URISyntaxException use)
        {
            return null;
        }
    }

    public ExperimentDataHandler findDataHandler()
    {
        return Handler.Priority.findBestHandler(ExperimentServiceImpl.get().getExperimentDataHandlers(), (ExpData)this);
    }

    public String getDataFileUrl()
    {
        return _object.getDataFileUrl();
    }

    @Nullable
    public File getFile()
    {
        return _object.getFile();
    }

    public boolean isInlineImage()
    {
        return null != getFile() && MIME_MAP.isInlineImageFor(getFile());
    }

    public String urlFlag(boolean flagged)
    {
        String ret = null;
        if (getLSID() != null)
        {
            DataType type = getDataType();
            if (type != null)
            {
                ret = type.urlFlag(flagged);
            }
            if (ret != null)
                return ret;
        }
        if (flagged)
        {
            return AppProps.getInstance().getContextPath() + "/Experiment/flagData.png";
        }
        return AppProps.getInstance().getContextPath() + "/Experiment/images/unflagData.png";
    }

    public void delete(User user)
    {
        ExperimentServiceImpl.get().deleteDataByRowIds(getContainer(), Collections.singleton(getRowId()));
    }
    
    public String getMimeType()
    {
        if (null != getDataFileUrl())
            return MIME_MAP.getContentTypeFor(getDataFileUrl());
        else
            return null;
    }

    public boolean isFileOnDisk()
    {
        File f = getFile();
        return f != null && NetworkDrive.exists(f) && f.isFile();
    }

    public String getCpasType()
    {
        String result = _object.getCpasType();
        return result == null ? ExpData.DEFAULT_CPAS_TYPE : result;
    }

    public void setGenerated(boolean generated)
    {
        ensureUnlocked();
        _object.setGenerated(generated);
    }

    @Override
    public boolean isGenerated()
    {
        return _object.isGenerated();
    }

    @Override
    @Nullable
    public ExpDataClassImpl getDataClass()
    {
        if (_object.getClassId() != null)
            return ExperimentServiceImpl.get().getDataClass(_object.getClassId());

        return null;
    }

    public void importDataFile(PipelineJob job, XarSource xarSource) throws ExperimentException
    {
        String dataFileURL = getDataFileUrl();

        if (dataFileURL == null)
        {
            return;
        }

        if (xarSource.shouldIgnoreDataFiles())
        {
            job.debug("Skipping load of data file " + dataFileURL + " based on the XAR source");
            return;
        }

        try
        {
            job.debug("Trying to load data file " + dataFileURL + " into the system");

            File file = new File(new URI(dataFileURL));

            if (!file.exists())
            {
                job.debug("Unable to find the data file " + file.getPath() + " on disk.");
                return;
            }

            // Check that the file is under the pipeline root to prevent users from referencing a file that they
            // don't have permission to import
            PipeRoot pr = PipelineService.get().findPipelineRoot(job.getContainer());
            if (!xarSource.allowImport(pr, job.getContainer(), file))
            {
                if (pr == null)
                {
                    job.warn("No pipeline root was set, skipping load of file " + file.getPath());
                    return;
                }
                job.debug("The data file " + file.getAbsolutePath() + " is not under the folder's pipeline root: " + pr + ". It will not be loaded directly, but may be loaded if referenced from other files that are under the pipeline root.");
                return;
            }

            ExperimentDataHandler handler = findDataHandler();
            try
            {
                handler.importFile(this, file, job.getInfo(), job.getLogger(), xarSource.getXarContext());
            }
            catch (ExperimentException e)
            {
                throw new XarFormatException(e);
            }

            job.debug("Finished trying to load data file " + dataFileURL + " into the system");
        }
        catch (URISyntaxException e)
        {
            throw new XarFormatException(e);
        }
    }
}
