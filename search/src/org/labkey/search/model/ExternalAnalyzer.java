package org.labkey.search.model;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballAnalyzer;

/**
 * User: adam
 * Date: Apr 19, 2010
 * Time: 9:02:20 PM
 */
public enum ExternalAnalyzer
{
    SnowballAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            return new SnowballAnalyzer(LuceneSearchServiceImpl.LUCENE_VERSION, "English");
        }},
    KeywordAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            return new KeywordAnalyzer();
        }};

    abstract Analyzer getAnalyzer();
}
