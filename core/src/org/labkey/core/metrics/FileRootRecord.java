package org.labkey.core.metrics;

import java.util.Date;

public record FileRootRecord(int rowId, String entityId, Long fileRootSize, Date LastCrawled) {}
