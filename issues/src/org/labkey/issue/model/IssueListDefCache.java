/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.issue.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 9/27/2016.
 */
public class IssueListDefCache
{
    private static final Cache<Container, IssueDefCollections> ISSUE_DEF_DB_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "Issue List Definition Cache", new CacheLoader<Container, IssueDefCollections>()
    {
        @Override
        public IssueDefCollections load(Container c, @Nullable Object argument)
        {
            return new IssueDefCollections(c);
        }
    });

    private static class IssueDefCollections
    {
        private final Map<Integer, IssueListDef> _rowIdMap;
        private final Map<String, IssueListDef> _nameMap;
        private final Map<String, List<IssueListDef>> _domainKindMap;

        private IssueDefCollections(Container c)
        {
            Map<Integer, IssueListDef> rowIdMap = new HashMap<>();
            Map<String, IssueListDef> nameMap = new HashMap<>();
            Map<String, List<IssueListDef>> domainKindMap = new HashMap<>();

            new TableSelector(IssuesSchema.getInstance().getTableInfoIssueListDef(), SimpleFilter.createContainerFilter(c), null).forEach(issueDef -> {

                rowIdMap.put(issueDef.getRowId(), issueDef);
                nameMap.put(issueDef.getName(), issueDef);

                if (!domainKindMap.containsKey(issueDef.getKind()))
                    domainKindMap.put(issueDef.getKind(), new ArrayList<>());

                domainKindMap.get(issueDef.getKind()).add(issueDef);

            }, IssueListDef.class);

            _rowIdMap = Collections.unmodifiableMap(rowIdMap);
            _nameMap = Collections.unmodifiableMap(nameMap);
            _domainKindMap = Collections.unmodifiableMap(domainKindMap);
        }

        private @Nullable IssueListDef getForRowId(int rowId)
        {
            return _rowIdMap.get(rowId);
        }

        private @Nullable IssueListDef getForName(String name)
        {
            return _nameMap.get(name);
        }

        private @NotNull Collection<IssueListDef> getListDefs()
        {
            return _rowIdMap.values();
        }

        private @NotNull Collection<IssueListDef> getForDomainKind(String kindName)
        {
            if (_domainKindMap.containsKey(kindName))
                return _domainKindMap.get(kindName);
            else
                return Collections.emptyList();
        }
    }

    static @Nullable IssueListDef getIssueListDef(Container c, int rowId)
    {
        return ISSUE_DEF_DB_CACHE.get(c).getForRowId(rowId);
    }

    static @Nullable IssueListDef getIssueListDef(Container c, String name)
    {
        return ISSUE_DEF_DB_CACHE.get(c).getForName(name);
    }

    static @NotNull Collection<IssueListDef> getIssueListDefs(Container c)
    {
        return ISSUE_DEF_DB_CACHE.get(c).getListDefs();
    }

    static @NotNull Collection<IssueListDef> getForDomainKind(Container c, String kindName)
    {
        return ISSUE_DEF_DB_CACHE.get(c).getForDomainKind(kindName);
    }

    public static void uncache(Container c)
    {
        ISSUE_DEF_DB_CACHE.remove(c);
    }
}
