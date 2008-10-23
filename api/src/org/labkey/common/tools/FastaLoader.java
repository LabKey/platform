/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.common.tools;

import java.io.*;
import java.util.*;

/**
 * User: migra
 * Date: Jun 16, 2004
 * Time: 2:40:58 PM
 *
 */
public class FastaLoader
{
    private File _fastaFile;


//    int[] _aaCounts = new int[26];
    public FastaLoader(File fastaFile)
    {
        _fastaFile = fastaFile;
    }

    /*
    public int[] getFrequencies()
    {
        return _aaCounts;
    }
    */
    public ProteinIterator iterator()
    {
        return new ProteinIterator();
    }

    public class ProteinIterator implements Iterator<Protein>
    {
        String _proteinHeader = null;
        BufferedReader _reader = null;
        private boolean _beforeFirst = true;
        private long _fileLength;
        private long _currentLine = 0;
        private long _currentHeaderLine = 0;
        private long _lastHeaderLine = 0;
        private long _currentPosition = 0;
        private Integer _previous = null;

        private void init()
        {
            try
            {
                _reader = new BufferedReader(new FileReader(_fastaFile));
                String line = getLine();

                //Iterator expects _proteinHeader to be initialized...
                if (null != line && line.charAt(0) == '>')
                {
                    _proteinHeader = line.substring(1);
                    _currentHeaderLine = _currentLine;
                }
                else
                {
                    if (null != _reader)
                        _reader.close();

                    throw new IllegalArgumentException("Fasta File did not start with a >");
                }
            }
            catch (IOException x)
            {
                if (null != _reader)
                {
                    try
                    {
                        _reader.close();
                    }
                    catch (IOException x2) {}
                }
            }

            _beforeFirst = false;
            _fileLength = _fastaFile.length();
        }

        private String getLine() throws IOException
        {
            String line = _reader.readLine();

            if (null != line)
            {
                // TODO: Temporary tracking of input position by counting length of each line... should
                // switch to a different input stream/reader that tracks bytes instead
                _currentPosition += line.length() + 1;
                _currentLine++;
            }

            return line;
        }

        /**
         *
         * @return are there any more proteins left in the file
         */
        public boolean hasNext()
        {
            if (_beforeFirst)
                init();

            return null != _proteinHeader;
        }

        /**
         * Closes file just in case.
         * @throws IOException if file is not closeable
         */
        protected void finalize() throws Throwable
        {
            super.finalize();    //If iteration is not complete, still close the file...
            if (null != _reader)
                _reader.close();
        }

        /**
         * Get next protein object in file.
         * @return Protein or null if end of file
         */
        public Protein next()
        {
            if (_beforeFirst)
                init();

            if (null == _proteinHeader)
                return null;

            ByteArrayOutputStream aaStream = new ByteArrayOutputStream(2048);
            String line;

            try
            {
                while((line = getLine()) != null)
                {
                    if (line.length() > 0 && line.charAt(0) == '>')
                    {
                        Protein p = createProtein(_proteinHeader, aaStream);
                        _proteinHeader = line.substring(1);
                        return p;
                    }
                    else
                    {
                        byte[] bytes = line.getBytes();
                        for (byte aByte : bytes)
                        {
                            if ((aByte >= 'A') && (aByte <= 'Z')) {
                                //_aaCounts[bytes[i] - 'A'] ++;
                                aaStream.write(aByte);
                            }
                        }
                    }
                }
                
                // End of file -- last protein
                Protein p = createProtein(_proteinHeader, aaStream);
                close();
                return p;
            }
            catch (IOException x)
            {
                throw new RuntimeException("Failed to read next protein", x);
            }

        }

        private Protein createProtein(String header, ByteArrayOutputStream aaStream)
        {
            Protein p = new Protein(header, aaStream.toByteArray());
            _lastHeaderLine = _currentHeaderLine;
            _currentHeaderLine = _currentLine;
            return p;
        }

        /**
         * Unsupported
         */
        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        /**
         * Closes the file. No more items will be returned from the iterator
         */
        public void close()
        {
            if (null != _reader)
                try
                {
                    _reader.close();
                }
                catch (IOException x) {}

            _reader = null;
            _proteinHeader = null;
        }

        public Integer getPercentCompleteIfChanged()
        {
            int current = getPercentComplete();

            if (null != _previous && current == _previous.intValue())
                return null;

            _previous = current;
            return current;
        }


        private int getPercentComplete()
        {
            return Math.round((float)_currentPosition * 100 / _fileLength);
        }


        public long getLastHeaderLine()
        {
            return _lastHeaderLine;
        }
    }
}
