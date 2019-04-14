package org.labkey.api.view.template;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.Path;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

public class FilePathClientDependency extends ClientDependency
{
    private static final Logger _log = Logger.getLogger(FilePathClientDependency.class);

    protected final Path _filePath;

    protected FilePathClientDependency(Path filePath, ModeTypeEnum.@NotNull Enum mode, TYPE primaryType)
    {
        super(primaryType, mode);
        _filePath = filePath;
    }

    @Override
    protected void init()
    {
        processScript(_filePath);
    }

    @Override
    protected String getUniqueKey()
    {
        assert _filePath != null;
        return getCacheKey(_filePath.toString(), _mode);
    }

    @Override
    public String getScriptString()
    {
        return _filePath.toString();
    }

    private void processScript(Path filePath)
    {
        TYPE type = TYPE.fromPath(filePath);

        if (type == null)
        {
            _log.warn("Invalid file type for resource: " + filePath);
            return;
        }

        handleScript(filePath);
    }

    protected void handleScript(Path filePath)
    {
        if (!_mode.equals(ModeTypeEnum.PRODUCTION))
            _devModePath = filePath.toString();

        if (!_mode.equals(ModeTypeEnum.DEV))
            _prodModePath = filePath.toString();
    }
}
