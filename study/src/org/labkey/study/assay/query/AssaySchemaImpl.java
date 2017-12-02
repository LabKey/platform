/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.study.assay.query;

import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: brittp
 * Date: Jun 28, 2007
 * Time: 11:07:09 AM
 */
public class AssaySchemaImpl extends AssaySchema
{
    private Map<ExpProtocol, AssayProvider> _protocols;
    private Collection<AssayProvider> _allProviders;

    /** Cache the "child" schemas so that we don't have to recreate them over and over within this schema's lifecycle */
    private Map<ExpProtocol, AssayProtocolSchema> _protocolSchemas = new HashMap<>();
    private Map<AssayProvider, AssayProviderSchema> _providerSchemas = new HashMap<>();

    static public class Provider extends DefaultSchema.SchemaProvider
    {
        public Provider(@Nullable Module module)
        {
            super(module);
        }

        @Override
        public boolean isAvailable(DefaultSchema schema, Module module)
        {
            return true;
        }

        public QuerySchema createSchema(DefaultSchema schema, Module module)
        {
            return new AssaySchemaImpl(schema.getUser(), schema.getContainer(), null);
        }
    }

    public AssaySchemaImpl(User user, Container container, @Nullable Container targetStudy)
    {
        this(user, container, ExperimentService.get().getSchema(), targetStudy);
    }

    private AssaySchemaImpl(User user, Container container, DbSchema schema, @Nullable Container targetStudy)
    {
        super(NAME, user, container, schema, targetStudy);
    }

    @Override
    public Set<String> getTableNames()
    {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(ASSAY_LIST_TABLE_NAME);
        names.add(ASSAY_PROVIDERS_TABLE_NAME);
        return Collections.unmodifiableSet(names);
    }

    private Map<ExpProtocol, AssayProvider> getProtocols()
    {
        if (_protocols == null)
        {
            _protocols = new HashMap<>();
            for (ExpProtocol protocol : AssayService.get().getAssayProtocols(getContainer()))
            {
                _protocols.put(protocol, AssayService.get().getProvider(protocol));
            }
        }
        return _protocols;
    }

    @Override
    public QuerySchema getSchema(String name)
    {
        if (_restricted)
            return null;

        for (AssayProvider provider : getAllProviders())
        {
            if (name.equalsIgnoreCase(provider.getResourceName()))
            {
                return getProviderSchema(provider);
            }
        }

        // fallback to the provider's display name
        for (AssayProvider provider : getAllProviders())
        {
            if (name.equalsIgnoreCase(provider.getName()))
                return getProviderSchema(provider);
        }

        return super.getSchema(name);
    }

    // Get the cached AssayProviderSchema for a provider or create a new one.
    private AssayProviderSchema getProviderSchema(AssayProvider provider)
    {
        AssayProviderSchema providerSchema = _providerSchemas.get(provider);
        if (providerSchema == null)
        {
            providerSchema = provider.createProviderSchema(getUser(), getContainer(), getTargetStudy());
            assert providerSchema != null;
            _providerSchemas.put(provider, providerSchema);
        }
        return providerSchema;
    }

    @Override
    public Set<String> getSchemaNames()
    {
        if (_restricted)
            return Collections.emptySet();

        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(super.getSchemaNames());
        for (AssayProvider provider : getProtocols().values())
            names.add(provider.getResourceName());

        return names;
    }

    private Collection<AssayProvider> getAllProviders()
    {
        if (_allProviders == null)
        {
            _allProviders = AssayService.get().getAssayProviders();
        }
        return _allProviders;
    }


    @Override
    public TableInfo createTable(String name)
    {
        if (name.equalsIgnoreCase(ASSAY_LIST_TABLE_NAME))
            return new AssayListTable(this);

        if (name.equalsIgnoreCase(ASSAY_PROVIDERS_TABLE_NAME))
            return new AssayProviderTable(this);

        // For backward compatibility with <12.2, resolve "protocolName tableName" tables.
        for (Map.Entry<ExpProtocol, AssayProvider> entry : getProtocols().entrySet())
        {
            ExpProtocol protocol = entry.getKey();
            AssayProvider provider = entry.getValue();
            String prefix = protocol.getName().toLowerCase() + " ";
            if (name.toLowerCase().startsWith(prefix))
            {
                // Cut off the prefix part that's no longer a part of the table name and is
                // now part of the schema name
                String newName = name.substring(prefix.length());
                AssayProtocolSchema protocolSchema = getProtocolSchema(protocol, provider);

                // We only need to check this for tables in the schema, not custom queries since they didn't get
                // moved as part of the refactor
                if (protocolSchema != null && new CaseInsensitiveHashSet(protocolSchema.getTableNames()).contains(newName))
                {
                    return protocolSchema.getTable(newName);
                }
            }
        }

        return null;
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, org.springframework.validation.BindException errors)
    {
        // For backward compatibility with <12.2, resolve runs and results query views.
        String name = settings.getQueryName();
        if (name != null)
        {
            for (Map.Entry<ExpProtocol, AssayProvider> entry : getProtocols().entrySet())
            {
                ExpProtocol protocol = entry.getKey();
                AssayProvider provider = entry.getValue();
                String prefix = protocol.getName().toLowerCase() + " ";
                if (name.toLowerCase().startsWith(prefix))
                {
                    AssayProtocolSchema protocolSchema = getProtocolSchema(protocol, provider);
                    // Cut off the prefix part that's no longer a part of the table name and is
                    // now part of the schema name
                    String newName = name.substring(prefix.length());

                    // We only need to check this for tables in the schema, not custom queries since they didn't get
                    // moved as part of the refactor
                    if (protocolSchema != null && new CaseInsensitiveHashSet(protocolSchema.getTableNames()).contains(newName))
                    {
                        // Switch the name to reflect the new, preferred location for these queries
                        settings.setSchemaName(protocolSchema.getSchemaPath().toString());
                        settings.setQueryName(newName);
                        return protocolSchema.createView(context, settings, errors);
                    }
                }
            }
        }

        return super.createView(context, settings, errors);
    }

    private AssayProtocolSchema getProtocolSchema(ExpProtocol protocol, AssayProvider provider)
    {
        AssayProtocolSchema protocolSchema = _protocolSchemas.get(protocol);
        if (protocolSchema == null)
        {
            protocolSchema = provider.createProtocolSchema(getUser(), getContainer(), protocol, getTargetStudy());
            _protocolSchemas.put(protocol, protocolSchema);
        }
        return protocolSchema;
    }

    public static class TestCase extends Assert
    {
        private Mockery _context;
        private ExpProtocol _protocol1;
        private AssayProvider _provider1;
        private AssaySchemaImpl _schemaImpl;

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

                allowing(_provider1).createProviderSchema(with(any(User.class)), with(any(Container.class)), with(same((Container)null)));
            }});

            Map<ExpProtocol, AssayProvider> designs = new HashMap<>();
            designs.put(_protocol1, _provider1);

            _schemaImpl = new AssaySchemaImpl(User.guest, ContainerManager.createMockContainer(), null, null);
            _schemaImpl._protocols = designs;
            _schemaImpl._allProviders = Collections.singletonList(_provider1);
        }

        @Test
        public void testProviderChildSchemas()
        {
            assertEquals(new HashSet<>(Arrays.asList(AssaySchema.ASSAY_LIST_TABLE_NAME, AssaySchema.ASSAY_PROVIDERS_TABLE_NAME)), _schemaImpl.getTableNames());
            assertEquals(PageFlowUtil.set("Folder", "Provider1"), _schemaImpl.getSchemaNames());

            assertNotNull(_schemaImpl.getSchema("Provider1"));
        }

        // Would like to be able to validate this, but right now classloading for AbstractTableInfo, which is
        // part of a method signature in AssayProtocolSchema, sets an ActionURL static variable which needs
        // the context path

//        @Test
//        public void testLegacyQueryName()
//        {
//            final AssayProtocolSchema protocolSchema = _context.mock(AssayProtocolSchema.class);
//
//            _context.checking(new Expectations() {{
//                allowing(protocolSchema).getTableNames();
//                will(returnValue(Collections.singleton("Data")));
//
//                allowing(protocolSchema).getTable("Data", true);
//            }});
//
//            _context.checking(new Expectations() {{
//                allowing(_provider1).createProtocolSchema(with(any(User.class)), with(any(Container.class)), with(same(_protocol1)), (Container) with(same(null)));
//                will(returnValue(protocolSchema));
//            }});
//
//            assertNotNull(_schemaImpl.getTable("Design1 Data"));
//        }
    }
}
