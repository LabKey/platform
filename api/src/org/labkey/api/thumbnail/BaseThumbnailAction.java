package org.labkey.api.thumbnail;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ExportAction;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.services.ServiceRegistry;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
* User: adam
* Date: 10/18/11
* Time: 9:20 AM
*/
public abstract class BaseThumbnailAction<FORM> extends ExportAction<FORM>
{
    @Override
    protected String getCommandClassMethodName()
    {
        return "getProvider";  // getProvider() method determines the form class
    }

    // Do any additional permissions checks and return the provider (or null, if no thumbnail should be sent)
    public abstract @Nullable StaticThumbnailProvider getProvider(FORM form) throws Exception;

    @Override
    public void export(FORM form, HttpServletResponse response, BindException errors) throws Exception
    {
        ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

        if (null == svc)
            return;

        StaticThumbnailProvider provider = getProvider(form);

        if (null != provider)
        {
            CacheableWriter writer = svc.getThumbnailWriter(provider);

            // TODO: need to handle client caching better -- use long expiration and _dc to defeat caching
            Calendar expiration = new GregorianCalendar();
            expiration.add(Calendar.SECOND, 5);

            writer.writeToResponse(response, expiration);
        }
    }
}
