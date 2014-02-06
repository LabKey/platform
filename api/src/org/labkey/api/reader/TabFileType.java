/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.FileType;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * User: kevink
 * Date: 10/3/12
 */
class TabFileType extends FileType
{
    private final String delim;

    public TabFileType(List<String> suffixes, String defaultSuffix)
    {
        super(suffixes, defaultSuffix);
        if (".tsv".equals(defaultSuffix))
            delim = "\t";
        else if (".csv".equals(defaultSuffix))
            delim = ",";
        else
            throw new IllegalArgumentException("Only tsv or csv currently supported");
    }

    // CONSIDER: sniff for the actual separator and quote character in TabLoader instead?
    @Override
    public boolean isHeaderMatch(@NotNull byte[] header)
    {
        String s;
        try
        {
            s = new String(header, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }

        String[] lines = StringUtils.split(s, "\n\r");
        if (lines.length == 0)
            return false;

        int noFieldsCount = 0;
        int fieldLen = -1;
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i];
            if (line.length() == 0 || line.charAt(0) == TabLoader.COMMENT_CHAR)
                continue;

            String[] fields = line.split(delim, -1);
            if (fields.length == 1)
            {
                // Reject if too many lines have no fields
                noFieldsCount++;
                if (noFieldsCount == 2)
                    return false;

                continue;
            }

            if (fieldLen == -1)
            {
                // Assuming the first non-comment line is a header,
                // reject if we find an empty header or a non-alphanumeric string.
                for (String columnHeader : fields)
                {
                    columnHeader = columnHeader.trim();
                    if (columnHeader.length() == 0 || !StringUtils.isAsciiPrintable(columnHeader))
                        return false;
                }
                fieldLen = fields.length;
            }
            else if (fields.length > fieldLen)
            {
                // Reject if the fields are longer than the header (a line may have missing fields or may be the last incomplete line.)
                return false;
            }
        }

        // Reject if no lines using the delimiter were found.
        if (fieldLen == -1)
            return false;

        return true;
    }
}
