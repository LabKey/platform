package org.labkey.freezerpro.export;

/**
 * Created by klum on 2/2/2015.
 */
public class GetVialSamplesCommand extends ExportLocationCommand
{
    public GetVialSamplesCommand(FreezerProExport export, String url, String username, String password, int start, int limit)
    {
        super(export, url, username, password, start, limit);
    }
}
