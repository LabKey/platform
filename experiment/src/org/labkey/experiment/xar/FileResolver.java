/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.experiment.xar;

import org.labkey.api.util.FileUtil;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.xar.Replacer;

import java.io.File;
import java.io.IOException;
import java.io.FileFilter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * User: jeckels
 * Date: Jan 12, 2006
 */
public class FileResolver implements Replacer
{
    private final File _rootDir;

    private Map<String, List<File>> _unusedFileNames = new HashMap<>();
    private Map<String, List<String>> _unusedFileNameVariations = new HashMap<>();
    private Map<String, List<String>> _unusedFileBaseNames = new HashMap<>();

    private Set<String> _fileNamesToAdvance = new HashSet<>();
    private Set<String> _fileNameVariationsToAdvance = new HashSet<>();
    private Set<String> _fileBaseNamesToAdvance = new HashSet<>();

    private Map<File, String> _relativePathsCache = new HashMap<>();
    private Map<File, File[]> _dirContentsCache = new HashMap<>();

    public FileResolver(File rootDir)
    {
        _rootDir = rootDir;
    }

    private String getFileNameVariation(final String filter) throws XarFormatException
    {
        List<String> nameVariations = _unusedFileNameVariations.get(filter);
        if (nameVariations == null)
        {
            List<File> files = calculateFileList(filter);
            if (files.size() == 0)
            {
                throw new XarFormatException("No files found for FileBaseName filter " + filter);
            }

            nameVariations = new ArrayList<>(files.size());
            if (files.size() == 1)
            {
                nameVariations.add("");
            }
            else
            {
                List<String> names = new ArrayList<>(files.size());
                for (File f : files)
                {
                    names.add(f.getName());
                }


                int commonStart = getCommonCharStartCount(names);
                int commonEnd = getCommonCharEndCount(names);
                for (String s : names)
                {
                    if (commonEnd == s.length())
                    {
                        nameVariations.add("");
                    }
                    else
                    {
                        nameVariations.add(s.substring(commonStart, s.length() - commonEnd));
                    }
                }
            }
            _unusedFileNameVariations.put(filter, nameVariations);
        }

        if (nameVariations.isEmpty())
        {
            throw new XarFormatException("Insufficient files found for FileNameVariation filter " + filter);
        }
        _fileNameVariationsToAdvance.add(filter);
        return nameVariations.get(0);
    }

    private String getFileName(final String filter) throws XarFormatException
    {
        List<File> files = _unusedFileNames.get(filter);
        if (files == null)
        {
            files = calculateFileList(filter);
            _unusedFileNames.put(filter, files);
        }

        if (files.isEmpty())
        {
            throw new XarFormatException("Insufficient files found for FileName filter " + filter);
        }
        File f = files.get(0);
        _fileNamesToAdvance.add(filter);
        try
        {
            return FileUtil.relativize(_rootDir, f, true);
        }
        catch (IOException e)
        {
            throw new XarFormatException(e);
        }
    }

    private int getCommonCharStartCount(List<String> names)
    {
        int commonStart = 0;
        String firstName = names.get(0);
        while (commonStart < firstName.length())
        {
            char firstNameChar = firstName.charAt(commonStart);
            for (int i = 1; i < names.size(); i++)
            {
                String name = names.get(i);
                if (name.length() <= commonStart || name.charAt(commonStart) != firstNameChar)
                {
                    return commonStart;
                }
            }
            commonStart++;
        }
        return commonStart;
    }

    private int getCommonCharEndCount(List<String> names)
    {
        int commonEnd = 0;
        String firstName = names.get(0);
        while (commonEnd < firstName.length())
        {
            char firstNameChar = firstName.charAt(firstName.length() - commonEnd - 1);
            for (int i = 1; i < names.size(); i++)
            {
                String name = names.get(i);
                if (name.length() <= commonEnd || name.charAt(name.length() - commonEnd - 1) != firstNameChar)
                {
                    return commonEnd;
                }
            }
            commonEnd++;
        }
        return commonEnd;
    }

    private Pattern createPattern(String filter)
    {
        String[] parts = filter.split("\\*");
        StringBuilder sb = new StringBuilder();
        sb.append(Pattern.quote(parts[0]));
        for (int i = 1; i < parts.length; i++)
        {
            sb.append("(.*)");
            sb.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    private List<File> listFiles(File dir, FileFilter filter)
    {
        File[] allFiles = _dirContentsCache.get(dir);
        if (allFiles == null)
        {
            allFiles = dir.listFiles();
            _dirContentsCache.put(dir, allFiles);
        }
        List<File> result = new ArrayList<>();
        if (allFiles != null)
        {
            for (File f : allFiles)
            {
                if (filter.accept(f))
                {
                    result.add(f);
                }
            }
        }
        return result;
    }

    private List<File> calculateFileList(String fullFilter) throws XarFormatException
    {
        List<File> result = new ArrayList<>();
        String[] filters = fullFilter.split(";");
        for (String filter : filters)
        {
            final Pattern p = createPattern(filter);
            final IOException[] exception = new IOException[1];

            File dir;
            int index = filter.lastIndexOf("/");
            if (index != -1)
            {
                dir = new File(_rootDir, filter.substring(0, index));
            }
            else
            {
                dir = _rootDir;
            }

            List<File> filteredFiles = listFiles(dir, new FileFilter()
            {
                public boolean accept(File f)
                {
                    try
                    {
                        String relativePath = getRelativePath(f);

                        return p.matcher(relativePath).matches();
                    }
                    catch (IOException e)
                    {
                        exception[0] = e;
                        return false;
                    }
                }
            });
            if (exception[0] != null)
            {
                throw new XarFormatException("Unable to determine the list of matching files for " + filter, exception[0]);
            }

            result.addAll(filteredFiles);
        }

        Collections.sort(result);
        return result;
    }

    private String getRelativePath(File f)
            throws IOException
    {
        String relativePath = _relativePathsCache.get(f);
        if (relativePath == null)
        {
            relativePath = FileUtil.relativizeUnix(_rootDir, f, true);
            _relativePathsCache.put(f, relativePath);
        }
        return relativePath;
    }

    private String searchFileBaseName(File f, Pattern[] longPatterns, Pattern[] shortPatterns, String originalFilter) throws IOException, XarFormatException
    {
        for (int i = 0; i < longPatterns.length; i++)
        {
            Matcher longMatcher = longPatterns[i].matcher(getRelativePath(f));
            if (longMatcher.matches())
            {
                Matcher shortMatcher = shortPatterns[i].matcher(f.getName());
                StringBuilder sb = new StringBuilder();
                if (!shortMatcher.find())
                {
                    throw new XarFormatException("No base name found for filter " + originalFilter + " on filename " + f.getName());
                }
                if (shortMatcher.groupCount() == 0)
                {
                    String fileName = f.getName();
                    int dotIndex = fileName.indexOf(".");
                    if (dotIndex == -1)
                    {
                        sb.append(fileName);
                    }
                    else
                    {
                        sb.append(fileName.substring(0, dotIndex));
                    }
                }
                else
                {
                    for (int j = 1; j <= shortMatcher.groupCount(); j++)
                    {
                        sb.append(shortMatcher.group(j));
                    }
                }
                if (sb.length() == 0)
                {
                    throw new XarFormatException("No base name found for filter " + originalFilter + " on filename " + f.getName());
                }
                return sb.toString();
            }
        }
        throw new XarFormatException("No base name found for filter " + originalFilter + " on filename " + f.getName());
    }

    private String getFileBaseName(final String originalFilter) throws XarFormatException
    {
        List<String> names = _unusedFileBaseNames.get(originalFilter);
        if (names == null)
        {
            String[] filters = originalFilter.split(";");
            Pattern[] fullPatterns = new Pattern[filters.length];
            Pattern[] shortPatterns = new Pattern[filters.length];
            for (int i = 0; i < filters.length; i++)
            {
                String filter = filters[i];
                fullPatterns[i] = createPattern(filter);
                int index = filter.lastIndexOf('/');
                if (index != -1)
                {
                    filter = filter.substring(index + 1);
                }
                shortPatterns[i] = createPattern(filter);
            }

            List<File> files = calculateFileList(originalFilter);

            names = new ArrayList<>(files.size());
            for (File f : files)
            {
                try
                {
                    names.add(searchFileBaseName(f, fullPatterns, shortPatterns, originalFilter));
                }
                catch (IOException e)
                {
                    throw new XarFormatException(e);
                }
            }
            _unusedFileBaseNames.put(originalFilter, names);
        }

        if (names.isEmpty())
        {
            throw new XarFormatException("Insufficient files found for FileBaseName filter " + originalFilter);
        }
        _fileBaseNamesToAdvance.add(originalFilter);
        return names.get(0);
    }

    public void advance() throws XarFormatException
    {
        advanceMap(_unusedFileNames, _fileNamesToAdvance);
        advanceMap(_unusedFileNameVariations, _fileNameVariationsToAdvance);
        advanceMap(_unusedFileBaseNames, _fileBaseNamesToAdvance);
    }

    private void advanceMap(Map<String, ? extends List<? extends Object>> map, Set<String> filtersToAdvance)
    {
        for (Map.Entry<String, ? extends List<? extends Object>> entry : map.entrySet())
        {
            String filter = entry.getKey();
            if (filtersToAdvance.contains(filter))
            {
                entry.getValue().remove(0);
            }
        }
        filtersToAdvance.clear();
    }

    public void verifyEmpty() throws XarFormatException
    {
        verifyMapEmpty(_unusedFileNames, "FileName");
        verifyMapEmpty(_unusedFileNameVariations, "FileNameVariation");
        verifyMapEmpty(_unusedFileBaseNames, "FileBaseName");
    }

    private void verifyMapEmpty(Map<String, ? extends List<? extends Object>> map, String description)
            throws XarFormatException
    {
        for (Map.Entry<String, ? extends List<? extends Object>> entry : map.entrySet())
        {
            List<? extends Object> unused = entry.getValue();
            if (!unused.isEmpty())
            {
                throw new XarFormatException("Not all files for " + description + " filter " + entry.getKey() + " were used, there are still " + unused.size() + " left.");
            }
        }
    }

    public String getReplacement(String template) throws XarFormatException
    {
        if (template == null)
        {
            return null;
        }

        String filter = findFilter(template, "FilePath");
        if (filter != null)
        {
            return getFileName(filter);
        }

        filter = findFilter(template, "FileCount");
        if (filter != null)
        {
            return Integer.toString(calculateFileList(filter).size());
        }

        filter = findFilter(template, "FileNameVariation");
        if (filter != null)
        {
            return getFileNameVariation(filter);
        }

        filter = findFilter(template, "FileBaseName");
        if (filter != null)
        {
            return getFileBaseName(filter);
        }

        return null;
    }

    private String findFilter(String template, String functionName)
    {
        if (template.startsWith(functionName + "(") && template.endsWith(")"))
        {
            String filter = template.substring((functionName + "(").length(), template.length() - ")".length());
            return filter.replace('\\', '/');
        }
        return null;
    }

    public void reset()
    {
        _unusedFileNames.clear();
        _unusedFileBaseNames.clear();
        _unusedFileNameVariations.clear();

        _fileBaseNamesToAdvance.clear();
        _fileNamesToAdvance.clear();
        _fileNameVariationsToAdvance.clear();
    }
}
