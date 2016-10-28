/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
package org.labkey.search.model;

import com.google.common.collect.ImmutableMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;

import java.io.IOException;


/**
 * User: adam
 * Date: Apr 19, 2010
 * Time: 9:02:20 PM
 */
@SuppressWarnings({"UnusedDeclaration"})
public enum LuceneAnalyzer
{
    SimpleAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            return new SimpleAnalyzer();
        }},
    KeywordAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            return new KeywordAnalyzer();
        }},
    EnglishAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            return new EnglishAnalyzer();
        }},

    // A simple, non-stemming analyzer for identifiers. Tokenizes only on whitespace (all punctuation is left intact) and then lower-cases.
    IdentifierAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            try
            {
                return CustomAnalyzer.builder()
                    .withTokenizer(WhitespaceTokenizerFactory.class)
                    .addTokenFilter(LowerCaseFilterFactory.class)
                    .build();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }},

    // A hybrid analyzer that uses a non-stemming analyzer for categories and identifier fields and a stemming
    // English analyzer for all other fields. This is our standard analyzer that's optimized for our usage.
    LabKeyAnalyzer {
        @Override
        Analyzer getAnalyzer()
        {
            Analyzer identifierAnalyzer = IdentifierAnalyzer.getAnalyzer();

            return new PerFieldAnalyzerWrapper(LuceneAnalyzer.EnglishAnalyzer.getAnalyzer(), ImmutableMap.of(
                LuceneSearchServiceImpl.FIELD_NAME.searchCategories.name(), identifierAnalyzer,
                LuceneSearchServiceImpl.FIELD_NAME.identifiersLo.name(), identifierAnalyzer,
                LuceneSearchServiceImpl.FIELD_NAME.identifiersMed.name(), identifierAnalyzer,
                LuceneSearchServiceImpl.FIELD_NAME.identifiersHi.name(), identifierAnalyzer
            ));
        }};

    abstract Analyzer getAnalyzer();
}
