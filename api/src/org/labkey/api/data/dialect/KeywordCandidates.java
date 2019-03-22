/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.api.data.dialect;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Nov 15, 2010
 * Time: 3:33:14 PM
 */
public class KeywordCandidates
{
    private static final KeywordCandidates INSTANCE = new KeywordCandidates();
    private static final Logger LOG = Logger.getLogger(SqlDialect.class);

    private final Set<String> CANDIDATES;
    private final Set<String> SQL_2003_KEYWORDS;

    private KeywordCandidates()
    {
        try
        {
            List<String> sql2003 = PageFlowUtil.getStreamContentsAsList(KeywordCandidates.class.getResourceAsStream("sql2003Keywords.txt"), true);
            SQL_2003_KEYWORDS = new CaseInsensitiveHashSet(sql2003);

            List<String> candidates = PageFlowUtil.getStreamContentsAsList(KeywordCandidates.class.getResourceAsStream("sqlKeywords.txt"), true);
            CANDIDATES = new CaseInsensitiveHashSet(candidates);
            CANDIDATES.addAll(SQL_2003_KEYWORDS);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static KeywordCandidates get()
    {
        return INSTANCE;
    }

    public Set<String> getSql2003Keywords()
    {
        return SQL_2003_KEYWORDS;
    }

    public Set<String> getCandidates()
    {
        return CANDIDATES;
    }

    public boolean containsAll(Set<String> reservedWordSet, String productName)
    {
        Set<String> notFound = new HashSet<>();

        for (String word : reservedWordSet)
            if (!CANDIDATES.contains(word))
                notFound.add(word);

        if (notFound.isEmpty())
            return true;

        LOG.error("Keywords from " + productName + " dialect need to be added to sqlKeywords.txt file: " + notFound);

        return false;
    }
}
