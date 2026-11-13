/*
 * Copyright (c) 2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.reports;

import org.labkey.vfs.FileLike;

/** A script engine that runs against a copy of the working directory elsewhere, so scripts must name files by their path in the copy. */
public interface RemoteWorkingDirScriptEngine
{
    String getRemotePath(FileLike localFile);
}
