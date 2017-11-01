/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.Map;

/*
* User: adam
* Date: Mar 9, 2011
* Time: 10:26:44 PM
*/
public interface WritableIndexManager
{
    void clear();

    void deleteDocument(String id);

    IndexSearcher getSearcher() throws IOException;

    void releaseSearcher(IndexSearcher searcher) throws IOException;

    void deleteQuery(Query query) throws IOException;

    void index(String documentId, Document doc) throws IOException;

    void commit();

    void close() throws IOException, InterruptedException;

    Map<String, String> getIndexFormatProperties();

    Directory getCurrentDirectory();

    /**
     * Is this a real index manager?
     * @return Whether this manager points to a real search index
     */
    boolean isReal();
}
