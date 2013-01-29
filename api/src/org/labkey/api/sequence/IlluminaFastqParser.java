
/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.sequence;

import net.sf.picard.fastq.FastqReader;
import net.sf.picard.fastq.FastqRecord;
import net.sf.picard.fastq.FastqWriter;
import net.sf.picard.fastq.FastqWriterFactory;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Formats;
import org.labkey.api.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is designed to parse the FASTQ files produced by a single run on an illumina instructment and produce one gzipped FASTQ
 * for each sample in that run.  Parsing that CSV file to obtain the sample list is upstream of this class.
 * It is designed to be called from a pipeline job, although it should not need to be.
 *
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 4/18/12
 * Time: 11:35 AM
 */
public class IlluminaFastqParser
{
    private String _outputPrefix;
    private Map<Integer, Object> _sampleMap;
    private File _destinationDir;
    private File[] _files;
    private Map<Pair<Object, Integer>, Pair<File, FastqWriter>> _fileMap;
    private Map<Pair<Object, Integer>, Integer> _sequenceTotals;
    private Set<Integer> _skippedSampleIdx = new HashSet<Integer>();
    private FastqWriterFactory _writerFactory = new FastqWriterFactory();
    private Logger _logger;
    private static FileType FASTQ_FILETYPE = new FileType(Arrays.asList("fastq", "fq"), "fastq", FileType.gzSupportLevel.SUPPORT_GZ);

    public IlluminaFastqParser (@Nullable String outputPrefix, Map<Integer, Object> sampleMap, Logger logger, File... files)
    {
        _outputPrefix = outputPrefix;
        _sampleMap = sampleMap;
        _files = files;
        _logger = logger;
    }

    // NOTE: sampleMap maps the sample index used internally within illumina (ie. the order of this sample in the CSV), to a sampleId used
    // by the callee
    public IlluminaFastqParser (String outputPrefix, Map<Integer, Object> sampleMap, Logger logger, String sourcePath, String fastqPrefix)
    {
        _outputPrefix = outputPrefix;
        _sampleMap = sampleMap;
        _logger = logger;

        List<File> files = inferIlluminaInputsFromPath(sourcePath, fastqPrefix);
        _files = files.toArray(new File[files.size()]);
    }

    // because ilumina sample CSV files do not provide a clear way to identify the FASTQ files/
    // this method accepts the CSV input and an optional FASTQ file prefix.  it will return any
    // FASTQ files or zipped FASTQs in the same folder as the CSV and filter using the prefix, if provided.
    public static List<File> inferIlluminaInputsFromPath(String path, String fastqPrefix)
    {
        File folder = new File(path);
        List<File> _fastqFiles = new ArrayList<File>();

        for (File f : folder.listFiles())
        {
            if(!FASTQ_FILETYPE.isType(f))
                continue;

            if(fastqPrefix != null && !f.getName().startsWith(fastqPrefix))
                continue;

            _fastqFiles.add(f);
        }

        return _fastqFiles;
    }

    //this returns a map connecting samples with output FASTQ files.
    // the key of the map is a pair where the first item is the sampleId and the second item indicated whether this file is the forward (1) or reverse (2) reads
    public Map<Pair<Object, Integer>, File> parseFastqFiles () throws PipelineJobException
    {
        _fileMap = new HashMap<Pair<Object, Integer>, Pair<File, FastqWriter>>();
        _sequenceTotals = new HashMap<Pair<Object, Integer>, Integer>();

        FastqReader reader = null;
        int _parsedReads;

        try
        {
            for (File f : _files)
            {
                try
                {
                    _logger.info("Beginning to parse file: " + f.getName());
                    File targetDir = _destinationDir == null ? f.getParentFile() : _destinationDir;

                    _parsedReads = 0;

                    reader = new FastqReader(f);
                    FastqWriter writer;
                    while(reader.hasNext())
                    {
                        FastqRecord fq = reader.next();
                        String header = fq.getReadHeader();
                        IlluminaReadHeader parsedHeader = new IlluminaReadHeader(header);
                        int sampleIdx = parsedHeader.getSampleNum();
                        int pairNumber = parsedHeader.getPairNumber();

                        writer = getWriter(sampleIdx, targetDir, pairNumber);
                        if(writer != null)
                        {
                            writer.write(fq);

                            _parsedReads++;
                            updateCount(sampleIdx, pairNumber);

                            if (0 == _parsedReads % 25000)
                                logReadsProgress(_parsedReads);
                        }
                    }

                    if (0 != _parsedReads % 25000)
                        logReadsProgress(_parsedReads);

                    _logger.info("Finished parsing file: " + f.getName());
                    reader.close();
                    reader = null;
                }
                catch (Exception e)
                {
                    for (Pair<Object, Integer> key :_fileMap.keySet())
                    {
                        _fileMap.get(key).getValue().close();
                    }

                    throw new PipelineJobException(e);
                }
                finally
                {
                    if (reader != null)
                        reader.close();
                }
            }
        }
        finally
        {
            for (Pair<File, FastqWriter> pair : _fileMap.values())
            {
                if (pair.second != null)
                    pair.second.close();
            }
        }

        Map<Pair<Object, Integer>, File> outputs = new HashMap<Pair<Object, Integer>, File>();
        for (Pair<Object, Integer> key :_fileMap.keySet())
        {
            _fileMap.get(key).getValue().close();
            outputs.put(key, _fileMap.get(key).getKey());
        }

        return outputs;
    }

    private void logReadsProgress(int count)
    {
        String formattedCount = Formats.commaf0.format(count);
        _logger.info(formattedCount + " reads processed");
    }

    private void updateCount(int sampleIdx, int pairNumber)
    {
        if (_sampleMap.containsKey(sampleIdx))
        {
            Pair<Object, Integer> key = Pair.of(_sampleMap.get(sampleIdx), pairNumber);

            Integer total = _sequenceTotals.get(key);
            if (total == null)
                total = 0;

            total++;

            _sequenceTotals.put(key, total);
        }
    }

    private FastqWriter getWriter (int sampleIdx, File targetDir, int pairNumber) throws IOException, PipelineJobException
    {
        if(!_sampleMap.containsKey(sampleIdx))
        {
            if (!_skippedSampleIdx.contains(sampleIdx))
            {
                _logger.warn("The CSV input does not contain sample info for a sample with index: " + sampleIdx);
                _skippedSampleIdx.add(sampleIdx);
            }
            return null;
        }

        // NOTE: sampleIdx is the index of the sample according to the Illumina CSV file, and the number assigned
        // by the illumina barcode callers.  Sample 0 always refers to control reads
        Object sampleId = _sampleMap.get(sampleIdx);
        Pair<Object, Integer> sampleKey = Pair.of(sampleId, pairNumber);
        if (_fileMap.containsKey(sampleKey))
        {
            return _fileMap.get(sampleKey).getValue();
        }
        else
        {
            String name = (_outputPrefix == null ? "Reads" : _outputPrefix) + "-R" + pairNumber + "-" + (sampleIdx == 0 ? "Control" : sampleId) + ".fastq";
            File newFile = new File(targetDir, name);
            newFile.createNewFile();
            FastqWriter writer = _writerFactory.newWriter(newFile);

            _fileMap.put(Pair.of(sampleId, pairNumber), Pair.of(newFile, writer));
            return writer;
        }
    }

    public File getDestinationDir()
    {
        return _destinationDir;
    }

    public void setDestinationDir(File destinationDir)
    {
        _destinationDir = destinationDir;
    }

    public Map<Pair<Object, Integer>, Integer> getReadCounts()
    {
        return _sequenceTotals;
    }

    public File[] getFiles()
    {
        return _files;
    }

    public static class IlluminaReadHeader
    {
        private String _instrument;
        private int _runId;
        private String _flowCellId;
        private int _flowCellLane;
        private int _tileNumber;
        private int _xCoord;
        private int _yCoord;
        private int _pairNumber;
        private boolean _failedFilter;
        private int _controlBits;
        private int _sampleNum;

        public IlluminaReadHeader (String header) throws IllegalArgumentException
        {
            try
            {
                String[] h = header.split(":| ");

                if(h.length < 10)
                    throw new IllegalArgumentException("Improperly formatted header: " + header);

                _instrument = h[0];
                _runId = Integer.parseInt(h[1]);
                _flowCellId = h[2];
                _flowCellLane = Integer.parseInt(h[3]);
                _tileNumber = Integer.parseInt(h[4]);
                _xCoord = Integer.parseInt(h[5]);
                _yCoord = Integer.parseInt(h[6]);
                _pairNumber = Integer.parseInt(h[7]);
                setFailedFilter(h[8]);
                _controlBits = Integer.parseInt(h[9]);
                _sampleNum = Integer.parseInt(h[10]);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        public String getInstrument()
        {
            return _instrument;
        }

        public void setInstrument(String instrument)
        {
            _instrument = instrument;
        }

        public int getRunId()
        {
            return _runId;
        }

        public void setRunId(int runId)
        {
            _runId = runId;
        }

        public String getFlowCellId()
        {
            return _flowCellId;
        }

        public void setFlowCellId(String flowCellId)
        {
            _flowCellId = flowCellId;
        }

        public int getFlowCellLane()
        {
            return _flowCellLane;
        }

        public void setFlowCellLane(int flowCellLane)
        {
            _flowCellLane = flowCellLane;
        }

        public int getTileNumber()
        {
            return _tileNumber;
        }

        public void setTileNumber(int tileNumber)
        {
            _tileNumber = tileNumber;
        }

        public int getxCoord()
        {
            return _xCoord;
        }

        public void setxCoord(int xCoord)
        {
            _xCoord = xCoord;
        }

        public int getyCoord()
        {
            return _yCoord;
        }

        public void setyCoord(int yCoord)
        {
            _yCoord = yCoord;
        }

        public int getPairNumber()
        {
            return _pairNumber;
        }

        public void setPairNumber(int pairNumber)
        {
            _pairNumber = pairNumber;
        }

        public boolean isFailedFilter()
        {
            return _failedFilter;
        }

        public void setFailedFilter(boolean failedFilter)
        {
            _failedFilter = failedFilter;
        }

        public void setFailedFilter(String failedFilter)
        {
            _failedFilter = "Y".equals(failedFilter) ? true : false;
        }

        public int getControlBits()
        {
            return _controlBits;
        }

        public void setControlBits(int controlBits)
        {
            _controlBits = controlBits;
        }

        public int getSampleNum()
        {
            return _sampleNum;
        }

        public void setSampleNum(int sampleNum)
        {
            this._sampleNum = sampleNum;
        }
    }

    public static IlluminaReadHeader parseHeader(String header)
    {
        try
        {
            return new IlluminaReadHeader(header);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}


