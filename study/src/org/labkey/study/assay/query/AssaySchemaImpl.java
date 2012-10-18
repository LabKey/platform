/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

    /** Cache the "child" schemas so that we don't have to recreate them over and over within this schema's lifecycle */
    private Map<ExpProtocol, AssayProtocolSchema> _protocolSchemas = new HashMap<ExpProtocol, AssayProtocolSchema>();
    private Map<AssayProvider, AssayProviderSchema> _providerSchemas = new HashMap<AssayProvider, AssayProviderSchema>();

    static public class Provider extends DefaultSchema.SchemaProvider
    {
        public QuerySchema getSchema(DefaultSchema schema)
        {
            return new AssaySchemaImpl(schema.getUser(), schema.getContainer(), null);
        }
    }

    public AssaySchemaImpl(User user, Container container, @Nullable Container targetStudy)
    {
        super(NAME, user, container, ExperimentService.get().getSchema(), targetStudy);
    }

    @Override
    public Set<String> getVisibleTableNames()
    {
        return Collections.singleton(ASSAY_LIST_TABLE_NAME);
    }

    public Set<String> getTableNames()
    {
        Set<String> names = new TreeSet<String>(new Comparator<String>()
        {
            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });

        names.addAll(getVisibleTableNames());

        return names;
    }

    private Map<ExpProtocol, AssayProvider> getProtocols()
    {
        if (_protocols == null)
        {
            _protocols = new HashMap<ExpProtocol, AssayProvider>();
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

        for (AssayProvider provider : AssayService.get().getAssayProviders())
        {
            if (name.equalsIgnoreCase(provider.getResourceName()))
            {
                return getProviderSchema(provider);
            }
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

        Set<String> names = new TreeSet<String>(new Comparator<String>()
        {
            public int compare(String o1, String o2)
            {
                return o1.compareToIgnoreCase(o2);
            }
        });
        names.addAll(super.getSchemaNames());
        for (AssayProvider provider : AssayService.get().getAssayProviders())
            names.add(provider.getResourceName());

        return names;
    }

    @Override
    public TableInfo createTable(String name)
    {
        if (name.equalsIgnoreCase(ASSAY_LIST_TABLE_NAME))
            return new AssayListTable(this);

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
}
