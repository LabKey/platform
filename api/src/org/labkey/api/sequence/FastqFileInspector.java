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
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 5/5/12
 * Time: 9:03 AM
 */
public class FastqFileInspector
{
    private File _file;
    private int _min;
    private int _max;
    private int _total;
    private float _avg;

    public FastqFileInspector(File f)
    {
        _file = f;
    }

    public void processFile()
    {
        FileType gz = new FileType(".gz");
        File toProcess = _file;

        try
        {
            if(gz.isType(_file))
            {
                toProcess = File.createTempFile("fastqMetrics", ".fastq");
                Compress.decompressGzip(_file, toProcess);
            }

            _total = 0;
            int sum = 0;
            _min = 0;
            _max = 0;

            FastqReader reader = new FastqReader(_file);
            int l;
            while(reader.hasNext())
            {
                FastqRecord fq = reader.next();
                l = fq.getReadString().length();

                _total++;
                if(l < _min || _min == 0)
                    _min = l;
                if(l > _max)
                    _max = l;

                sum += l;
            }

            _avg = sum / _total;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if(!toProcess.equals(_file))
            {
                toProcess.delete();
            }
        }
    }

    public Map<String, Object> getMetricsMap()
    {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("Total Sequences", _total);
        map.put("Min Sequence Length", _min);
        map.put("Max Sequence Length", _max);
        map.put("Avg Sequence Length", _avg);
        return map;
    }

    public int getMin()
    {
        return _min;
    }

    public int getMax()
    {
        return _max;
    }

    public int getTotal()
    {
        return _total;
    }

    public float getAvg()
    {
        return _avg;
    }
}
