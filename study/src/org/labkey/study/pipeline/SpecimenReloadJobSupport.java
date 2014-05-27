package org.labkey.study.pipeline;

import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.FileType;

import java.io.File;

/**
 * Created by klum on 5/24/2014.
 */
public interface SpecimenReloadJobSupport extends SpecimenJobSupport
{
    void setSpecimenArchive(File archiveFile);

    String getSpecimenTransform();

    SpecimenTransform.ExternalImportConfig getExternalImportConfig();
}
