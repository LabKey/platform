package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: kevink
 * Date: 10/13/12
 */
public class AssayProviderSchema extends AssaySchema
{
    private final AssayProvider _provider;

    private List<ExpProtocol> _protocols;
    private Map<String, ExpProtocol> _protocolsByName;

    /** Cache the "child" schemas so that we don't have to recreate them over and over within this schema's lifecycle */
    private Map<ExpProtocol, AssayProtocolSchema> _protocolSchemas = new HashMap<ExpProtocol, AssayProtocolSchema>();

    public AssayProviderSchema(User user, Container container, @NotNull AssayProvider provider, @Nullable Container targetStudy)
    {
        this(user, container, provider, targetStudy, null);
    }

    public AssayProviderSchema(User user, Container container, @NotNull AssayProvider provider, @Nullable Container targetStudy, @Nullable List<ExpProtocol> protocols)
    {
        super(SchemaKey.fromParts(AssaySchema.NAME, provider.getResourceName()), descr(provider), user, container, ExperimentService.get().getSchema(), targetStudy);
        _provider = provider;
        _protocols = protocols;
        if (protocols != null)
        {
            _protocolsByName = new HashMap<String, ExpProtocol>();
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
     * Get ExpProtocols for this AssayProvider.
     * @return
     */
    @NotNull
    public Collection<ExpProtocol> getProtocols()
    {
        if (_protocols == null)
        {
            _protocols = AssayService.get().getAssayProtocols(getContainer(), getProvider());
            _protocolsByName = new HashMap<String, ExpProtocol>();
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
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return Collections.emptySet();
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

}
