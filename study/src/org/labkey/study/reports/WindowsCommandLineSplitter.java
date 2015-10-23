/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.study.reports;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.ArrayList;

/**
 * User: adam
 * Date: 6/23/13
 * Time: 3:30 PM
 */

// This class splits a Windows command line into its component parts, respecting the arcane Microsoft C parsing rules
// documented here: http://msdn.microsoft.com/en-us/library/a1y7w461.aspx  Note that this doesn't actually parse the
// arguments... all quotes and backslashes remain as specified.  This code includes a hack to detect and handle commands
// that include a space in the path.
//
// For 13.2, we invoked this code in an attempt to keep legacy reports working. In 13.3 (after validating that the code
// below works in the vast majority of cases), we added an upgrade task to correct existing commands (quoting them, etc.)
// in the reports. We'll remove this class in 16.1.
public class WindowsCommandLineSplitter implements CommandLineSplitter
{
    @Override
    public String[] getCommandStrings(String commandLine) throws IOException
    {
        ArrayList<String> tokens = new ArrayList<>();
        CharBuffer cb = CharBuffer.wrap(commandLine);

        // Legacy parsing tries to mimic the ProcessBuilder behavior pre-Java 7u21. See #18077 for more details.
        // If the command is quoted, then don't use legacy mode (assume admin knows what they're doing).
        boolean legacyParsing = !commandLine.trim().startsWith("\"");
        String token;

        while (null != (token = getNextToken(cb, legacyParsing)))
        {
            tokens.add(token);
            legacyParsing = false;  // Only do this on the first token (the command), not the arguments
        }

        return tokens.toArray(new String[tokens.size()]);
    }

    private @Nullable String getNextToken(CharBuffer cb, boolean legacyParsing) throws IOException
    {
        StringBuilder ret = new StringBuilder();
        skipSpaces(cb);
        boolean quoted = false;

        loop:
        while (cb.hasRemaining())
        {
            char c = cb.get();

            switch(c)
            {
                case(' '):
                case('\t'):
                    if (quoted)
                    {
                        break;
                    }

                    if (legacyParsing)
                    {
                        // This is the hack that deals with legacy advanced reports having embedded spaces in the command,
                        // which Java 7u21 stopped supporting. If we're in legacyParsing mode, look ahead to the next
                        // slash or backslash (if there is one). If that path represents a valid directory then we'll keep
                        // parsing.
                        String whatsLeft = cb.toString();
                        int index = Math.min(whatsLeft.indexOf("\\"), whatsLeft.indexOf("/"));

                        if (-1 != index)
                        {
                            String upThroughSlash = c + whatsLeft.substring(0, index);
                            File testDir = new File(ret.toString() + upThroughSlash);

                            if (testDir.isDirectory())
                            {
                                ret.append(upThroughSlash);
                                cb.position(cb.position() + index);
                                continue;
                            }
                        }
                    }

                    // In all other cases (not legacy, no more slashes/backslashes, not valid directory) this token is over
                    break loop;
                case('"'):
                    quoted = !quoted;
                    break;
                case('\\'):
                    String backslashes = getBackslahes(cb);
                    ret.append(backslashes);

                    // Rules for one or more backslashes followed by a double quote
                    if (cb.hasRemaining() && '"' == cb.get())
                    {
                        int count = backslashes.length();

                        // Odd number of backslashes means escaped double quote
                        if (0 != count % 2)
                        {
                            ret.append('"');
                            continue;
                        }
                    }

                    // Even number of backslashes OR not followed by a double quote... in either case, we treat next character normally
                    cb.position(cb.position() - 1);
                    continue;
            }

            ret.append(c);
        }

        if (0 == ret.length())
            return null;
        else
            return ret.toString();
    }

    private String getBackslahes(CharBuffer cb)
    {
        StringBuilder sb = new StringBuilder("\\");

        while (cb.hasRemaining())
        {
            if ('\\' == cb.get())
            {
                sb.append('\\');
            }
            else
            {
                cb.position(cb.position() - 1);
                break;
            }
        }

        return sb.toString();
    }

    private void skipSpaces(CharBuffer cb) throws IOException
    {
        while (cb.hasRemaining())
        {
            char c = cb.get();

            if (' ' != c && '\t' != c)
            {
                cb.position(cb.position() - 1);
                break;
            }
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test() throws IOException
        {
            test("\"a b c\" d e", new String[]{"\"a b c\"", "d", "e"});
            test("\"ab\\\"c\" \"\\\\\" d", new String[]{"\"ab\\\"c\"", "\"\\\\\"", "d"});
            test("a\\\\\\b d\"e f\"g h", new String[]{"a\\\\\\b", "d\"e f\"g", "h"});
            test("a\\\\\\\"b c d", new String[]{"a\\\\\\\"b", "c", "d"});
            test("a\\\\\\\\\"b c\" d e", new String[]{"a\\\\\\\\\"b c\"", "d", "e"});

            test("\"C:\\Program Files\\Java\\jdk1.7.0_25\\bin\\java\" -cp c:/labkey/server/test/build/classes org.labkey.test.util.Echo ${DATA_FILE} ${REPORT_FILE}",
                new String[]{"\"C:\\Program Files\\Java\\jdk1.7.0_25\\bin\\java\"", "-cp", "c:/labkey/server/test/build/classes", "org.labkey.test.util.Echo", "${DATA_FILE}", "${REPORT_FILE}"});
            test("\"C:\\Program Files\\Java with \\\"embedded quote\\\"\\jdk1.7.0_25\\bin\\java\" -cp c:/labkey/server/test/build/classes org.labkey.test.util.Echo ${DATA_FILE} ${REPORT_FILE}",
                new String[]{"\"C:\\Program Files\\Java with \\\"embedded quote\\\"\\jdk1.7.0_25\\bin\\java\"", "-cp", "c:/labkey/server/test/build/classes", "org.labkey.test.util.Echo", "${DATA_FILE}", "${REPORT_FILE}"});

//      This test is highly machine-dependent... enable and adjust the path to test backward compatibility for unquoted commands that include spaces on Windows
//            test("C:\\Program Files\\Java\\jdk1.7.0_25\\bin\\java -cp c:/labkey/server/test/build/classes org.labkey.test.util.Echo ${DATA_FILE} ${REPORT_FILE}",
//                new String[]{"C:\\Program Files\\Java\\jdk1.7.0_25\\bin\\java", "-cp", "c:/labkey/server/test/build/classes", "org.labkey.test.util.Echo", "${DATA_FILE}", "${REPORT_FILE}"});
        }

        private void test(String commandLine, String[] expected) throws IOException
        {
            CommandLineSplitter parser = new WindowsCommandLineSplitter();
            String[] commands = parser.getCommandStrings(commandLine);
            assertArrayEquals(expected, commands);
        }
    }
}
