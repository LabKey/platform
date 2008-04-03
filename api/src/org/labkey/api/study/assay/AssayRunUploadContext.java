package org.labkey.api.study.assay;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.apache.struts.upload.MultipartRequestHandler;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Collection;
import java.io.File;
import java.io.IOException;

/**
 * User: brittp
* Date: Jul 11, 2007
* Time: 1:24:10 PM
*/
public interface AssayRunUploadContext
{
    ExpProtocol getProtocol();

    Map<PropertyDescriptor, String> getRunProperties();

    Collection<String> getSampleIds();

    Map<PropertyDescriptor, String> getUploadSetProperties();

    String getComments();

    String getName();

    User getUser();

    Container getContainer();

    HttpServletRequest getRequest();

    ActionURL getActionURL();

    Map<String, File> getUploadedData() throws IOException, ExperimentException;

    AssayProvider getProvider();

    MultipartRequestHandler getMultipartRequestHandler();
}
