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
package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Child schema asociated with a particular assay provider. Typical implementations will expose separate child
 * schemas for each of their assay designs that are in scope. Shared value lists may also be directly exposed as
 * separate queries.
 *
 * User: kevink
 * Date: 10/13/12
 */
public class AssayProviderSchema extends AssaySchema
{
    private final AssayProvider _provider;

    private List<ExpProtocol> _protocols;
    private Map<String, ExpProtocol> _protocolsByName;

    /** Cache the "child" schemas so that we don't have to recreate them over and over within this schema's lifecycle */
    private Map<ExpProtocol, AssayProtocolSchema> _protocolSchemas = new HashMap<>();

    public AssayProviderSchema(User user, Container container, @NotNull AssayProvider provider, @Nullable Container targetStudy)
    {
        this(user, container, provider, targetStudy, null);
    }

    public AssayProviderSchema(User user, Container container, @NotNull AssayProvider provider, @Nullable Container targetStudy, @Nullable List<ExpProtocol> protocols)
    {
        this(user, container, provider, targetStudy, protocols, ExperimentService.get().getSchema());
    }

    private AssayProviderSchema(User user, Container container, @NotNull AssayProvider provider, @Nullable Container targetStudy, @Nullable List<ExpProtocol> protocols, DbSchema dbSchema)
    {
        super(SchemaKey.fromParts(AssaySchema.NAME, provider.getResourceName()), descr(provider), user, container, dbSchema, targetStudy);
        _provider = provider;
        _protocols = protocols;
        if (protocols != null)
        {
            _protocolsByName = new HashMap<>();
            for (ExpProtocol protocol : protocols)
                _protocolsByName.put(protocol.getName(), protocol);
        }
    }

    private static String descr(AssayProvider provider)
    {
        return String.format("Contains data about all assay definitions of assay type %s", provider.getName());
    }

    @NotNull
    public AssayProvider getProvider()
    {
        return _provider;
    }

    /**
     * Get all protocols (assay designs) that are in scope for this AssayProvider.
     */
    @NotNull
    public Collection<ExpProtocol> getProtocols()
    {
        if (_protocols == null)
        {
            _protocols = AssayService.get().getAssayProtocols(getContainer(), getProvider());
            _protocolsByName = new CaseInsensitiveHashMap<>();
            for (ExpProtocol protocol : _protocols)
                _protocolsByName.put(protocol.getName(), protocol);
        }
        return _protocols;
    }

    @NotNull
    protected Map<String, ExpProtocol> getProtocolsByName()
    {
        if (_protocols == null)
            getProtocols();
        return _protocolsByName;
    }

    @Override
    public TableInfo createTable(String name)
    {
        // Default is to not provide any queries directly
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        // Default is to not provide any queries directly
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSchemaNames()
    {
        if (_restricted)
            return Collections.emptySet();

        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(super.getSchemaNames());
        for (ExpProtocol protocol : getProtocols())
            names.add(protocol.getName());

        return names;
    }

    @Override
    public QuerySchema getSchema(String name)
    {
        if (_restricted)
            return null;

        getProtocols();
        ExpProtocol protocol = getProtocolsByName().get(name);
        if (protocol != null)
            return getProtocolSchema(protocol);

        return super.getSchema(name);
    }

    // Get the cached AssayProtocolSchema for a protocol or create a new one.
    private AssayProtocolSchema getProtocolSchema(ExpProtocol protocol)
    {
        AssayProtocolSchema protocolSchema = _protocolSchemas.get(protocol);
        if (protocolSchema == null)
        {
            protocolSchema = _provider.createProtocolSchema(getUser(), getContainer(), protocol, getTargetStudy());
            assert protocolSchema != null;
            //if (protocolSchema == null)
            //    protocolSchema = new AssayProtocolSchema(_user, _container, protocol, provider);
            if (protocolSchema != null)
                _protocolSchemas.put(protocol, protocolSchema);
        }
        return protocolSchema;
    }

    public static class TestCase extends Assert
    {
        private Mockery _context;
        private ExpProtocol _protocol1;
        private AssayProvider _provider1;
        private AssayProviderSchema _schemaImpl;

        public TestCase()
        {
            _context = new Mockery();
            _context.setImposteriser(ClassImposteriser.INSTANCE);

            _protocol1 = _context.mock(ExpProtocol.class);
            _context.checking(new Expectations() {{
                allowing(_protocol1).getName();
                will(returnValue("Design1"));
            }});

            _provider1 = _context.mock(AssayProvider.class);
            _context.checking(new Expectations() {{
                allowing(_provider1).getName();
                will(returnValue("Provider1"));
                allowing(_provider1).getResourceName();
                will(returnValue("Provider1"));
            }});

            _schemaImpl = new AssayProviderSchema(User.guest, ContainerManager.createMockContainer(), _provider1, null, Collections.singletonList(_protocol1), null);
        }

        @Test
        public void testChildSchemas()
        {
            assertEquals(0, _schemaImpl.getTableNames().size());
            assertEquals(PageFlowUtil.set("Folder", "Design1"), _schemaImpl.getSchemaNames());
        }
    }
}
