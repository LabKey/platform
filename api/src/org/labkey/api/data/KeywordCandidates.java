package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.labkey.api.collections.Sets;
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
            SQL_2003_KEYWORDS = Sets.newCaseInsensitiveHashSet(sql2003);

            List<String> candidates = PageFlowUtil.getStreamContentsAsList(KeywordCandidates.class.getResourceAsStream("sqlKeywords.txt"), true);
            CANDIDATES = Sets.newCaseInsensitiveHashSet(candidates);
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

    public Set<String> getSql2003Keywords() throws IOException
    {
        return SQL_2003_KEYWORDS;
    }

    public boolean containsAll(Set<String> reservedWordSet, String productName)
    {
        Set<String> notFound = new HashSet<String>();

        for (String word : reservedWordSet)
            if (!CANDIDATES.contains(word))
                notFound.add(word);

        if (notFound.isEmpty())
            return true;

        LOG.error("Keywords from " + productName + " dialect need to be added to sqlKeywords.txt file: " + notFound);

        return false;
    }
}
