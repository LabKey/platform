package org.labkey.pipeline.mule.transformers;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;
import org.mule.umo.UMOEventContext;
import org.mule.umo.transformer.TransformerException;

public class XmlToObject extends org.mule.transformers.xml.XmlToObject
{
    private boolean _securityInitialized = false;

    @Override
    public Object transform(Object src, String encoding, UMOEventContext context) throws TransformerException
    {
        // Allow XStream to deserialize into any class. We are using this internally and trust the content of
        // the messages since the JMS endpoint is considered secure.
        // https://x-stream.github.io/security.html#framework
        if (!_securityInitialized)
        {
            XStream x = getXStream();
            x.addPermission(AnyTypePermission.ANY);
            _securityInitialized = true;
        }
        return super.transform(src, encoding, context);
    }
}
