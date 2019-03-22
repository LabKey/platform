/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.reader;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

/**
 * Users of FastaLoader implementations can simply iterate the loader itself (if all they want is a stream of Ts) or
 * they can grab the iterator to monitor progress, current line, etc.
 *
 * User: migra
 * Date: Jun 16, 2004
 */
public abstract class FastaLoader<T> implements Iterable<T>
{
    private final File _fastaFile;
    private final FastaIteratorElementFactory<T> _factory;
    private CharacterFilter _characterFilter = new UppercaseCharacterFilter();

    protected FastaLoader(File fastaFile, FastaIteratorElementFactory<T> factory)
    {
        _fastaFile = fastaFile;
        _factory = factory;
    }

    // Force subclasses to implement to provide callers a more appropriate name.
    public abstract FastaIterator iterator();

    public void setCharacterFilter(CharacterFilter characterFilter)
    {
        _characterFilter = characterFilter;
    }

    public interface FastaIteratorElementFactory<U>
    {
        public U createNext(String header, byte[] body);
    }

    public class FastaIterator implements Iterator<T>
    {
        private String _header = null;
        private BufferedReader _reader = null;

        // Various measures of where we are in the file to help with progress and error reporting.
        private boolean _beforeFirst = true;
        private long _fileLength;
        private long _currentLineNum = 0;
        private long _currentHeaderLineNum = 0;
        private long _lastHeaderLineNum = 0;
        private long _currentCharPosition = 0;
        private Integer _previous = null;

        private void init()
        {
            try
            {
                // Detect Charset encoding based on BOM
                _reader = Readers.getBOMDetectingReader(_fastaFile.getName().toLowerCase().endsWith(".gz") ? new GZIPInputStream(new FileInputStream(_fastaFile)): new FileInputStream(_fastaFile));

                String line = getLine();

                //Iterator expects _header to be initialized... strip initial ">"
                if (null != line && line.length() > 0 && line.charAt(0) == '>')
                {
                    _header = line.substring(1);
                    _currentHeaderLineNum = _currentLineNum;
                }
                else
                {
                    if (null != _reader)
                        _reader.close();

                    throw new IllegalArgumentException("Invalid FASTA file. The file did not start with \">\".");
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
                _currentCharPosition += line.length() + 1;
                _currentLineNum++;
            }

            return line;
        }

        /**
         *
         * @return are there any more sequences left in the file
         */
        public boolean hasNext()
        {
            if (_beforeFirst)
                init();

            return null != _header;
        }

        /**
         * Closes file just in case.
         * @throws java.io.IOException if file is not closeable
         */
        protected void finalize() throws Throwable
        {
            super.finalize();    //If iteration is not complete, still close the file...
            if (null != _reader)
                _reader.close();
        }

        /**
         * Get next entry in file.
         * @return T or null if end of file
         */
        public T next()
        {
            if (_beforeFirst)
                init();

            if (null == _header)
                return null;

            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream(2048);
            String line;

            try
            {
                while((line = getLine()) != null)
                {
                    if (line.length() > 0 && line.charAt(0) == '>')
                    {
                        T next = createNextElement(_header, bodyStream);
                        _header = line.substring(1);
                        return next;
                    }
                    else
                    {
                        char[] chars = line.toCharArray();

                        for (char c : chars)
                        {
                            if (_characterFilter.accept(c))
                            {
                                bodyStream.write(c);
                            }
                        }
                    }
                }

                // End of file -- last entry
                T next = createNextElement(_header, bodyStream);
                close();
                return next;
            }
            catch (IOException x)
            {
                throw new RuntimeException("Failed to read next FASTA sequence", x);
            }
        }

        private T createNextElement(String header, ByteArrayOutputStream aaStream)
        {
            T next = _factory.createNext(header, aaStream.toByteArray());
            _lastHeaderLineNum = _currentHeaderLineNum;
            _currentHeaderLineNum = _currentLineNum;
            return next;
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
            _header = null;
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
            return Math.round((float) _currentCharPosition * 100 / _fileLength);
        }


        public long getLastHeaderLineNum()
        {
            return _lastHeaderLineNum;
        }
    }

    public interface CharacterFilter
    {
        public boolean accept(char c);
    }

    public static class UpperAndLowercaseCharacterFilter implements CharacterFilter
    {
        @Override
        public boolean accept(char c)
        {
            return ((c >= 'A') && (c <= 'Z')) || ((c >= 'a') && (c <= 'z'));
        }
    }

    public static class UppercaseCharacterFilter implements CharacterFilter
    {
        @Override
        public boolean accept(char c)
        {
            return (c >= 'A') && (c <= 'Z');
        }
    }
}
