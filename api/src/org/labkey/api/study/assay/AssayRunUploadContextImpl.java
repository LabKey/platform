package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/18/13
 */
public class AssayRunUploadContextImpl<ProviderType extends AssayProvider> implements AssayRunUploadContext<ProviderType>
{
    private static final String FILE_INPUT_NAME = "file";

    // Required fields
    private final ExpProtocol _protocol;
    private final ProviderType _provider;
    private final User _user;
    private final Container _container;

    // Optional fields
    private final ViewContext _context;
    private final String _comments;
    private final String _name;
    private final String _targetStudy;
    private final Integer _reRunId;
    private final Map<String, String> _rawRunProperties;
    private final Map<String, String> _rawBatchProperties;

    // Lazily created fields
    private Map<String, File> _uploadedData;
    private Map<DomainProperty, String> _runProperties;
    private Map<DomainProperty, String> _batchProperties;

    // Mutable fields
    private TransformResult _transformResult;

    private AssayRunUploadContextImpl(Factory<ProviderType> factory)
    {
        _protocol = factory._protocol;
        _provider = factory._provider;
        _user = factory._user;
        _container = factory._container;
        _context = factory._context;

        _name = factory._name;
        _comments = factory._comments;

        _rawRunProperties = factory._rawRunProperties;
        _rawBatchProperties = factory._rawBatchProperties;
        _uploadedData = factory._uploadedData;

        _reRunId = factory._reRunId;
        _targetStudy = factory._targetStudy;
    }

    public static class Factory<ProviderType extends AssayProvider>
    {
        // Required fields
        private final ExpProtocol _protocol;
        private final ProviderType _provider;
        private final User _user;
        private final Container _container;

        // Optional fields
        private ViewContext _context;
        private String _comments;
        private String _name;
        private String _targetStudy;
        private Integer _reRunId;
        private Map<String, String> _rawRunProperties;
        private Map<String, String> _rawBatchProperties;
        private Map<String, File> _uploadedData;

        public Factory(
                @NotNull ExpProtocol protocol,
                @NotNull ProviderType provider,
                @NotNull ViewContext context)
        {
            this(protocol, provider, context.getUser(), context.getContainer());
            setViewContext(context);
        }

        public Factory(
                @NotNull ExpProtocol protocol,
                @NotNull ProviderType provider,
                @NotNull User user,
                @NotNull Container container)
        {
            _protocol = protocol;
            _provider = provider;
            _user = user;
            _container = container;
        }

        public Factory setViewContext(ViewContext context)
        {
            _context = context;
            return this;
        }

        public Factory setComments(String comments)
        {
            _comments = comments;
            return this;
        }

        public Factory setName(String name)
        {
            _name = name;
            return this;
        }

        public Factory setTargetStudy(String targetStudy)
        {
            _targetStudy = targetStudy;
            return this;
        }

        public Factory setReRunId(Integer reRunId)
        {
            _reRunId = reRunId;
            return this;
        }

        public Factory setBatchProperties(Map<String, String> rawProperties)
        {
            _rawBatchProperties = rawProperties;
            return this;
        }

        public Factory setRunProperties(Map<String, String> rawProperties)
        {
            _rawRunProperties = rawProperties;
            return this;
        }

        public Factory setUploadedData(Map<String, File> uploadedData)
        {
            _uploadedData = uploadedData;
            return this;
        }

        public AssayRunUploadContext<ProviderType> create()
        {
            return new AssayRunUploadContextImpl<>(this);
        }
    }

    @NotNull
    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        if (_runProperties == null)
        {
            Map<DomainProperty, String> properties = new HashMap<>();
            if (_rawRunProperties != null)
            {
                for (DomainProperty prop : _provider.getRunDomain(_protocol).getProperties())
                {
                    String value;
                    if (_rawRunProperties.containsKey(prop.getName()))
                        value = _rawRunProperties.get(prop.getName());
                    else
                        value = _rawRunProperties.get(prop.getPropertyURI());
                    properties.put(prop, value);
                }

            }
            _runProperties = properties;
        }
        return _runProperties;
    }

    public Map<DomainProperty, String> getBatchProperties()
    {
        if (_batchProperties == null)
        {
            Map<DomainProperty, String> properties = new HashMap<>();
            if (_rawBatchProperties != null)
            {
                for (DomainProperty prop : _provider.getBatchDomain(_protocol).getProperties())
                {
                    String value;
                    if (_rawBatchProperties.containsKey(prop.getName()))
                        value = _rawBatchProperties.get(prop.getName());
                    else
                        value = _rawBatchProperties.get(prop.getPropertyURI());
                    properties.put(prop, value);
                }

            }
            _batchProperties = properties;
        }
        return _batchProperties;
    }

    public String getComments()
    {
        return _comments;
    }

    public String getName()
    {
        return _name;
    }

    public User getUser()
    {
        return _user;
    }

    @NotNull
    public Container getContainer()
    {
        return _container;
    }

    public HttpServletRequest getRequest()
    {
        return _context != null ? _context.getRequest() : null;
    }

    public ActionURL getActionURL()
    {
        return _context != null ? _context.getActionURL() : null;
    }

    /**
     * Get the uploaded file data which will be imported.
     * The uploaded file is expected to be POSTed as a form-data parameter named '<code>file</code>'.
     *
     * @return A singleton map with key {@link AssayDataCollector#PRIMARY_FILE} and value of the uploaded file.
     * @throws ExperimentException
     */
    @NotNull
    public Map<String, File> getUploadedData() throws ExperimentException
    {
        if (_uploadedData == null && _context != null)
        {
            try
            {
                AssayDataCollector<AssayRunUploadContextImpl> collector = new FileUploadDataCollector(1, Collections.emptyMap(), FILE_INPUT_NAME);
                Map<String, File> files = collector.createData(this);
                // HACK: rekey the map using PRIMARY_FILE instead of FILE_INPUT_NAME
                _uploadedData = Collections.singletonMap(AssayDataCollector.PRIMARY_FILE, files.get(FILE_INPUT_NAME));
            }
            catch (IOException e)
            {
                throw new ExperimentException(e);
            }
        }
        return _uploadedData;
    }

    public ProviderType getProvider()
    {
        return _provider;
    }

    public Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Supported");
    }

    public Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Supported");
    }

    public void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Supported");
    }

    public void saveDefaultBatchValues() throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Supported");
    }

    public void saveDefaultRunValues() throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Supported");
    }

    public void clearDefaultValues(Domain domain) throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Supported");
    }

    @Override
    public Integer getReRunId()
    {
        return _reRunId;
    }

    public String getTargetStudy()
    {
        return _targetStudy;
    }

    public TransformResult getTransformResult()
    {
        return _transformResult == null ? DefaultTransformResult.createEmptyResult() : _transformResult;
    }

    @Override
    public void setTransformResult(TransformResult result)
    {
        _transformResult = result;
    }

    @Override
    public void uploadComplete(ExpRun run) throws ExperimentException
    {
        // no-op
    }
}
