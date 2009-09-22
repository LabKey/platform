package org.labkey.api.qc;

import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.ValidationException;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 22, 2009
 */
public interface DataValidator
{
    void validate(AssayRunUploadContext context, ExpRun run) throws ValidationException;
}
