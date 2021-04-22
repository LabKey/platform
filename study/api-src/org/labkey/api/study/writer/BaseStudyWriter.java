package org.labkey.api.study.writer;

import org.labkey.api.study.Study;
import org.labkey.api.writer.Writer;

public interface BaseStudyWriter<S extends Study, EC extends SimpleStudyExportContext> extends Writer<S, EC>
{
    boolean includeWithTemplate();
}
