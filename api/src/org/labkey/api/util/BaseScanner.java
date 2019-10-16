/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A generic scanner for that can perform simple text operations on various code languages (e.g., SQL, Java) while
 * ignoring comments and quoted strings. Subclasses for each language implement the scan() method to dictate specific
 * rules for that language (e.g., block comment, single line comment, quoting, escaped quotes, etc. rules).
 */
public abstract class BaseScanner
{
    protected final String _text;

    public BaseScanner(String text)
    {
        _text = text;
    }

    /**
     * Returns the index within the text of the first occurrence of the specified character, ignoring comments, quoted
     * strings, etc.
     * @param ch character to find
     * @return the index of the first occurrence of the character in the text if present, otherwise -1
     */
    public int indexOf(int ch)
    {
        return indexOf(ch, 0);
    }

    /**
     * Returns the index within the text of the first occurrence of the specific character starting at fromIndex, ignoring comments, quoted
     * strings, etc.
     * @param ch character to find
     * @param fromIndex index from which to start the search
     * @return the index of the first occurrence of the character in the text if present, otherwise -1
     */
    public int indexOf(int ch, int fromIndex)
    {
        // TODO: Reject ch == '"?
        MutableInt ret = new MutableInt(-1);

        scan(fromIndex, new Handler()
        {
            @Override
            public boolean character(char c, int index)
            {
                if (ch == c)
                {
                    ret.setValue(index);
                    return false;
                }
                else
                {
                    return true;
                }
            }
        });

        return ret.getValue();
    }

    /**
     * Returns the index within the text of the first occurrence of the specified substring, ignoring comments, quoted
     * strings, etc.
     * @param str the substring to search for
     * @return the index of the first occurrence of the specified substring in the text if present, otherwise -1
     */
    public int indexOf(String str)
    {
        return indexOf(str, 0);
    }

    /**
     * Returns the index within the text of the first occurrence of the specified substring starting at fromIndex, ignoring
     * comments, quoted strings, etc.
     * @param str the substring to search for
     * @param fromIndex index from which to start the search
     * @return the index of the first occurrence of the specified substring in the text if present, otherwise -1
     */
    public int indexOf(String str, int fromIndex)
    {
        // TODO: Reject str contains '"?
        MutableInt ret = new MutableInt(-1);

        scan(fromIndex, new Handler()
        {
            @Override
            public boolean character(char c, int index)
            {
                if (_text.regionMatches(index, str, 0, str.length()))
                {
                    ret.setValue(index);
                    return false;
                }
                else
                {
                    return true;
                }
            }
        });

        return ret.getValue();
    }

    /**
     * Splits the provided text into collections, using the specified separator. Ignores all comments as well as single-
     * and double-quoted strings (while correctly handling escaped quotes within those strings).
     * @param ch the character used as a delimiter
     * @return a collection of parsed strings
     */
    public Collection<String> split(char ch)
    {
        List<String> ret = new LinkedList<>();
        int start = 0;
        int idx = indexOf(ch);

        while (-1 != idx)
        {
            String subquery = StringUtils.mid(_text, start, idx - start);
            ret.add(subquery);
            start = idx + 1;
            idx = indexOf(ch, start);
        }

        String subquery = StringUtils.mid(_text, start, _text.length() - start);
        ret.add(subquery);

        return ret;
    }

    /**
     * Returns the text stripped of all comments (while correctly handling comment characters in quoted strings).
     * @return StringBuilder containing the stripped text
     */
    public StringBuilder stripComments()
    {
        StringBuilder ret = new StringBuilder();
        MutableInt previous = new MutableInt(0);

        scan(0, new Handler()
        {
            @Override
            public boolean comment(int startIndex, int endIndex)
            {
                ret.append(_text, previous.getValue(), startIndex);
                previous.setValue(endIndex);

                return true;
            }
        });

        ret.append(_text.substring(previous.getValue()));

        return ret;
    }

    /**
     * Scans the text, handling all comments and quoted strings (while correctly handling escaping rules within those
     * strings) and calling handler methods along the way. See subclasses for examples.
     * @param fromIndex index from which to start the scan
     * @param handler Handler whose methods will be invoked
     */
    public abstract void scan(int fromIndex, Handler handler);

    public interface Handler
    {
        /**
         * Called for every character outside of comments and quoted strings.
         * @param c Current character
         * @param index Index of that character
         * @return true to continue scanning, false to stop
         */
        default boolean character(char c, int index)
        {
            return true;
        }

        /**
         * Called for every comment detected.
         * @param startIndex   the starting index, inclusive
         * @param endIndex     the ending index, inclusive
         * @return             true to continue scanning, false to stop
         */
        default boolean comment(int startIndex, int endIndex)
        {
            return true;
        }

        /**
         * Called for every quoted string detected.
         * @param startIndex   the starting index, inclusive
         * @param endIndex     the ending index, inclusive
         * @return             true to continue scanning, false to stop
         */
        default boolean string(int startIndex, int endIndex)
        {
            return true;
        }
    }
}
