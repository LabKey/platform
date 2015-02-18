package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;

/**
 * Created by bimber on 9/15/2014.
 */
public interface ReferenceGenome extends Serializable
{
    /**
     * @return The FASTA file intended to be used during the pipeline, which is usually a copied version of an original FASTA or
     * a file created de novo by querying the sequence DB
     */
    //public @NotNull File getWorkingFastaFile();

    //public void setWorkingFasta(File workingFasta);

    /**
     * @return This is the original FASTA file usually created prior to the pipeline job.  This file may be copied to a working location
     * during the run.  The original FASTA may also have other resources cached in that directory, such as aligner indexes.
     * If this FASTA was created de novo during this run, it will exist on the webserver analysis directory for this job
     */
    public @NotNull File getSourceFastaFile();

    /**
     * @return This is the file that should typically be used by callers.  The pipeline code usually copies this file to the local working directory.
     * If this has occurred, that file will preferentially be used.  Otherwise, the source FASTA file will be returned.
     */
    public @NotNull File getWorkingFastaFile();

    public void setWorkingFasta(File workingFasta);

    /**
     * @return The FASTA index file associated with the working FASTA file
     */
    public File getFastaIndex();

    /**
     * @return The rowId of the corresponding record from
     */
    public Integer getGenomeId();

    /**
     * @return The rowId of the ExpData record matching the final version of the FASTA file.  If this is a saved reference,
     * this will correspond to the permanent FASTA, as opposed to the copy used in this job.  If this FASTA was created specifically for
     * this job then the FASTA will be in the analysis directory.
     */
    public Integer getFastaExpDataId();
}
