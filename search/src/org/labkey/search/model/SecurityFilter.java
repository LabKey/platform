/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.DocIdBitSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/*
* User: adam
* Date: Dec 16, 2009
* Time: 1:36:39 PM
*/
class SecurityFilter extends Filter
{
    private static final ContainerFieldSelector CONTAINER_FIELD_SELECTOR = new ContainerFieldSelector();
    private final User _user;
    private final Container _root;

    SecurityFilter(User user, Container root)
    {
        _user = user;
        _root = root;
    }

    @Override
    public DocIdSet getDocIdSet(IndexReader reader) throws IOException
    {
        int max = reader.maxDoc();
        BitSet bits = new BitSet(max);

        // TODO: Could short-circuit for administrators: bits.set(0, reader.maxDoc() - 1); -- but we want to test perf for now

        Set<Container> containers = ContainerManager.getAllChildren(_root, _user);
        Set<String> containerIds = new HashSet<String>(containers.size());

        for (Container c : containers)
            containerIds.add(c.getId());

        for (int i = 0; i < max; i++)
        {
            Document doc = reader.document(i, CONTAINER_FIELD_SELECTOR);

            String id = doc.get(LuceneSearchServiceImpl.FIELD_NAMES.container.name());

            if (null != id && containerIds.contains(id))
                bits.set(i);
        }

        return new DocIdBitSet(bits);
    }

    private static class ContainerFieldSelector implements FieldSelector
    {
        public FieldSelectorResult accept(String fieldName)
        {
            return LuceneSearchServiceImpl.FIELD_NAMES.container.name().equals(fieldName) ? FieldSelectorResult.LOAD_AND_BREAK : FieldSelectorResult.NO_LOAD;
        }
    }
}
