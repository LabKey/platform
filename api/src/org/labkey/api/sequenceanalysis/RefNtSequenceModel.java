/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.sequenceanalysis;

import com.drew.lang.annotations.Nullable;
import htsjdk.samtools.util.StringUtil;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.MemTracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Date;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Represents a record in sequenceanalysis.ref_nt_sequences.  Contains methods to assist with accessing/saving the sequence data,
 * which is stored in a gzipped text file on the filesystem.
 */
public class RefNtSequenceModel
{
    private static final Logger _log = Logger.getLogger(RefNtSequenceModel.class);

    private int _rowid;
    private String _name;
    //deprecated
    private String _sequence;
    private Integer _sequenceFile;
    private String _category;
    private String _subset;
    private String _locus;
    private String _lineage;
    private String _mol_type;
    private String _genbank;
    private String _ipd_accession;
    private String _species;
    private String _geographic_origin;
    private String _status;
    private String _aliases;
    private String _comments;
    private String _container;
    private String _createdby;
    private Date _created;
    private String _modifiedby;
    private Date _modified;

    private byte[] _sequenceBytes = null;

    public RefNtSequenceModel()
    {
        MemTracker.getInstance().put(this);
    }

    public static RefNtSequenceModel getForRowId(int rowId)
    {
        return new TableSelector(DbSchema.get("sequenceanalysis").getTable("ref_nt_sequences")).getObject(rowId, RefNtSequenceModel.class);
    }

    public int getRowid()
    {
        return _rowid;
    }

    public void setRowid(int rowid)
    {
        _rowid = rowid;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public @Nullable
    InputStream getSequenceInputStream() throws IOException
    {
        if (_sequenceFile != null)
        {
            ExpData d = ExperimentService.get().getExpData(_sequenceFile);
            if (d == null || !d.getFile().exists())
            {
                _log.error("unable to find sequence file for Id: " + getRowid());
                return null;
            }

            return new GZIPInputStream(new FileInputStream(d.getFile()));
        }

        return null;
    }

    public boolean hasSequenceFile()
    {
        if (_sequenceFile != null)
        {
            ExpData d = ExperimentService.get().getExpData(_sequenceFile);
            return d != null && d.getFile().exists();
        }

        return false;
    }

    public String getLegacySequence()
    {
        return _sequence;
    }

    public String getSequence()
    {
        //resolve from cached file
        if (_sequenceBytes == null && _sequenceFile != null)
        {
            //note: this will cache result
            getSequenceBases();
        }

        return _sequenceBytes == null ? null : StringUtil.bytesToString(_sequenceBytes);
    }

    @Deprecated
    public void setSequence(String sequence)
    {
        _sequence = sequence;
    }

    public Integer getSequenceFile()
    {
        return _sequenceFile;
    }

    public void setSequenceFile(Integer sequenceFile)
    {
        _sequenceFile = sequenceFile;
    }

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        _category = category;
    }

    public String getSubset()
    {
        return _subset;
    }

    public void setSubset(String subset)
    {
        _subset = subset;
    }

    public String getLocus()
    {
        return _locus;
    }

    public void setLocus(String locus)
    {
        _locus = locus;
    }

    public String getLineage()
    {
        return _lineage;
    }

    public void setLineage(String lineage)
    {
        _lineage = lineage;
    }

    public String getMol_type()
    {
        return _mol_type;
    }

    public void setMol_type(String mol_type)
    {
        _mol_type = mol_type;
    }

    public String getGenbank()
    {
        return _genbank;
    }

    public void setGenbank(String genbank)
    {
        _genbank = genbank;
    }

    public String getIpd_accession()
    {
        return _ipd_accession;
    }

    public void setIpd_accession(String ipd_accession)
    {
        _ipd_accession = ipd_accession;
    }

    public String getSpecies()
    {
        return _species;
    }

    public void setSpecies(String species)
    {
        _species = species;
    }

    public String getGeographic_origin()
    {
        return _geographic_origin;
    }

    public void setGeographic_origin(String geographic_origin)
    {
        _geographic_origin = geographic_origin;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public String getAliases()
    {
        return _aliases;
    }

    public void setAliases(String aliases)
    {
        _aliases = aliases;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getCreatedby()
    {
        return _createdby;
    }

    public void setCreatedby(String createdby)
    {
        _createdby = createdby;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public String getModifiedby()
    {
        return _modifiedby;
    }

    public void setModifiedby(String modifiedby)
    {
        _modifiedby = modifiedby;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public byte[] getSequenceBases()
    {
        if (_sequenceBytes == null)
        {
            try (InputStream is = getSequenceInputStream())
            {
                if (is != null)
                {
                    _sequenceBytes = IOUtils.toByteArray(is);
                }
            }
            catch (IOException e)
            {
                _log.error("Unable to read sequence for sequence Id: " + getRowid());
            }
        }

        return _sequenceBytes;
    }

    public void createFileForSequence(User u, String sequence) throws IOException
    {
        File output = getExpectedSequenceFile();
        if (output.exists())
        {
            output.delete();
        }
        output.createNewFile();

        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(output))))
        {
            writer.write(sequence);
            writer.close();
        }

        Container c = getLabKeyContainer();
        ExpData d = ExperimentService.get().createData(c, new DataType("Sequence Data"));
        d.setName(output.getName());
        d.setDataFileURI(output.toURI());
        d.save(u);

        setSequenceFile(d.getRowId());

        TableInfo ti = DbSchema.get("sequenceanalysis").getTable("ref_nt_sequences");

        Table.update(u, ti, this, _rowid);
    }

    private File getExpectedSequenceFile() throws IllegalArgumentException
    {
        return new File(getSequenceDir(true), _rowid + ".txt.gz");
    }

    private Container getLabKeyContainer()
    {
        Container c = ContainerManager.getForId(_container);
        if (c == null)
        {
            throw new IllegalArgumentException("Unable to find container: " + _container);
        }

        return c;
    }

    private File getSequenceDir(boolean create) throws IllegalArgumentException
    {
        Container c = getLabKeyContainer();
        FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
        File root = fileService == null ? null : fileService.getFileRoot(c, FileContentService.ContentType.files);
        if (root == null)
        {
            throw new IllegalArgumentException("File root not defined for container: " + c.getPath());
        }

        File ret = new File(root, ".sequences");
        if (create && !ret.exists())
        {
            ret.mkdirs();
        }

        return ret;
    }

    public void writeSequence(Writer writer, int lineLength) throws IOException
    {
        writeSequence(writer, lineLength, null, null);
    }

    public void writeSequence(Writer writer, int lineLength, Integer start, Integer end) throws IOException
    {
        byte[] seq = getSequenceBases();
        if (start != null || end != null)
        {
            if (start == null)
            {
                start = 0;
            }

            if (end == null)
            {
                end = seq.length;
            }

            seq = Arrays.copyOfRange(seq, start, end);
        }

        int len = seq.length;
        int count = 0;
        for (int i = 0; i < len; i++)
        {
            if (count == lineLength)
            {
                writer.write('\n');
                count = 0;
            }

            writer.write(seq[i]);
            count++;
        }

        //always terminate w/ a newline
        writer.write('\n');
    }

    public void clearCachedSequence()
    {
        _sequenceBytes = null;
    }
}
