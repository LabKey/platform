/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.labkey.study.importer.SpecimenImporter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.DateUtil;
import org.labkey.api.writer.ZipUtil;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * User: brittp
 * Date: Mar 14, 2006
 * Time: 5:04:38 PM
 */
public class SpecimenBatch extends StudyBatch implements Serializable
{
    public class EntryDescription
    {
        private final String _name;
        private final long _size;
        private final Date _date;

        public EntryDescription(String name, long size, Date date)
        {
            _name = name;
            _size = size;
            _date = date;
        }

        public Date getDate()
        {
            return _date;
        }

        public String getName()
        {
            return _name;
        }

        public long getSize()
        {
            return _size;
        }
    }

    public SpecimenBatch(ViewBackgroundInfo info, File definitionFile) throws SQLException
    {
        super(info, definitionFile);
    }


    public String getDescription()
    {
        String description = "Import specimens";
        if (_definitionFile != null)
            description += ": " + _definitionFile.getName();
        return description;
    }


    public void prepareImport(List<String> errors) throws IOException, SQLException
    {
      //  errors.add("Not Yet Implemented.");
    }

    public void run()
    {
        String status = PipelineJob.ERROR_STATUS;
        File unzipDir = null;
        try
        {
            info("Unzipping specimen archive " +  _definitionFile.getPath());
            String tempDirName = DateUtil.formatDateTime(new Date(), "yyMMddHHmmssSSS");
            unzipDir = new File(_definitionFile.getParentFile(), tempDirName);
            try
            {
                setStatus("Unzipping");
                List<File> files = ZipUtil.unzipToDirectory(_definitionFile, unzipDir, getLogger());
                info("Archive unzipped to " + unzipDir.getPath());
                info("Starting import...");
                setStatus("Processing");

                SpecimenImporter importer = new SpecimenImporter();
                importer.process(getUser(), getContainer(), files, getLogger());
                status = PipelineJob.COMPLETE_STATUS;
            }
            catch (Exception e)
            {
                status = PipelineJob.ERROR_STATUS;
                error("Unexpected error processing specimen archive", e);
            }
        }
        finally
        {
            delete(unzipDir);
            setStatus(status);
            _study = null;
        }
    }

    // Move to ZipUtil?
    public List<EntryDescription> getEntryDescriptions() throws IOException
    {
        List<EntryDescription> entryList = new ArrayList<EntryDescription>();
        ZipFile zip = null;
        try
        {
            zip = new ZipFile(_definitionFile);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory())
                    continue;
                entryList.add(new EntryDescription(entry.getName(), entry.getSize(), new Date(entry.getTime())));
            }
        }
        finally
        {
            if (zip != null) try { zip.close(); } catch (IOException e) {}
        }
        return entryList;
    }

    private void delete(File file)
    {
        if (file.isDirectory())
        {
            for (File child : file.listFiles())
                delete(child);
        }
        info("Deleting " + file.getPath());
        file.delete();
    }
}