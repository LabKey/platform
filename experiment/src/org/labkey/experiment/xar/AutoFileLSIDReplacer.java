package org.labkey.experiment.xar;

import org.labkey.api.data.Container;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.xar.Replacer;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: jeckels
 * Date: Jan 18, 2006
 */
public class AutoFileLSIDReplacer implements Replacer
{
    private final String _dataFileURL;
    private final Container _container;
    private final XarSource _source;

    public AutoFileLSIDReplacer(String dataFileURL, Container container, XarSource source)
    {
        _dataFileURL = dataFileURL;
        _container = container;
        _source = source;
    }

    public String getReplacement(String original) throws XarFormatException
    {
        if (original.equals("AutoFileLSID"))
        {
            if (_dataFileURL == null)
            {
                throw new XarFormatException("You must specify a dataFileURL when using AutoFileLSID");
            }

            String canonicalURL = _source.getCanonicalDataFileURL(_dataFileURL);

            ExpData data = ExperimentService.get().getExpDataByURL(canonicalURL, _container);
            if (data != null)
            {
                return data.getLSID();
            }
            else
            {
                return "urn:lsid:${LSIDAuthority}:${LSIDNamespace.Prefix}.Folder-${Container.RowId}-${XarFileId}:" + PageFlowUtil.encode(_dataFileURL);
            }
        }
        return null;
    }
}
