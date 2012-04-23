package org.labkey.api.sequence;

import net.sf.picard.fastq.*;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Formats;
import org.labkey.api.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

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
    private String _prefix;
    private List<Integer> _sampleList;
    private File _destinationDir;
    private File[] _files;
    private Map<Pair<Integer, Integer>, Pair<File, net.sf.picard.fastq.FastqWriter>> _fileMap;
    private FastqWriterFactory _writerFactory = new FastqWriterFactory();
    private Logger _logger;
    private static FileType FASTQ_FILETYPE = new FileType(Arrays.asList("fastq", "fq"), "fastq", FileType.gzSupportLevel.SUPPORT_GZ);

    public IlluminaFastqParser (String prefix, List<Integer> sampleList, Logger logger, File... files)
    {
        _prefix = prefix;
        _sampleList = sampleList;
        _files = files;
        _logger = logger;
    }

    public IlluminaFastqParser (String prefix, List<Integer> sampleList, Logger logger, String sourcePath)
    {
        _prefix = prefix;
        _sampleList = sampleList;
        _logger = logger;

        List<File> files = inferIlluminaInputsFromPath(sourcePath, prefix);
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
    public Map<Pair<Integer, Integer>, File> parseFastqFiles () throws PipelineJobException
    {
        _fileMap = new HashMap<Pair<Integer, Integer>, Pair<File, net.sf.picard.fastq.FastqWriter>>();
        FastqReader reader;
        int _parsedReads;

        for (File f : _files)
        {
            try
            {
                _logger.info("Beginning to parse file: " + f.getName());
                File targetDir = _destinationDir == null ? f.getParentFile() : _destinationDir;

                _parsedReads = 0;

                reader = new FastqReader(f);
                net.sf.picard.fastq.FastqWriter writer;
                while(reader.hasNext())
                {
                    FastqRecord fq = reader.next();
                    String header = fq.getReadHeader();
                    IlluminaReadHeader parsedHeader = new IlluminaReadHeader(header);
                    int sampleIdx = parsedHeader.getSampleNum();

                    writer = getWriter(sampleIdx, targetDir, parsedHeader.getPairNumber());
                    if(writer != null)
                        writer.write(fq);

                    _parsedReads++;
                    if (0 == _parsedReads % 25000)
                        logReadsProgress(_parsedReads);
                }

                if (0 != _parsedReads % 25000)
                    logReadsProgress(_parsedReads);

                _logger.info("Finished parsing file: " + f.getName());
                reader.close();

            }
            catch (Exception e)
            {
                for (Pair<Integer, Integer> key :_fileMap.keySet())
                {
                    _fileMap.get(key).getValue().close();
                }

                throw new PipelineJobException(e);
            }
        }

        Map<Pair<Integer, Integer>, File> outputs = new HashMap<Pair<Integer, Integer>, File>();
        for (Pair<Integer, Integer> key :_fileMap.keySet())
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

    private net.sf.picard.fastq.FastqWriter getWriter (int sampleIdx, File targetDir, int pairNumber) throws IOException, PipelineJobException
    {
        if(sampleIdx > _sampleList.size())
        {
            throw new PipelineJobException("The CSV input has more samples than expected");
        }

        //NOTE: sampleIdx is 1-based and the arrayList is 0-based, so we need to subtract 1
        //the element at position 0 represent control reads and those not mapped to a sample
        int sampleId = _sampleList.get(sampleIdx);
        Pair<Integer, Integer> sampleKey = Pair.of(sampleId, pairNumber);
        if (_fileMap.containsKey(sampleKey))
        {
            return _fileMap.get(sampleKey).getValue();
        }
        else
        {
            String name = _prefix + "-R" + pairNumber + "-" + (sampleId == 0 ? "Control" : sampleId) + ".fastq";
            File newFile = new File(targetDir, name);
            newFile.createNewFile();
            net.sf.picard.fastq.FastqWriter writer = _writerFactory.newWriter(newFile);

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

    public class IlluminaReadHeader
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
                    throw new IllegalArgumentException("Improperly formatted header");

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
}


