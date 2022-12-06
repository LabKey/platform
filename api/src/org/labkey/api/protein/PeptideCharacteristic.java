package org.labkey.api.protein;

import lombok.Data;

@Data
public class PeptideCharacteristic
{
    public static String COMBINED_PEPTIDE = "combined";
    public static String STACKED_PEPTIDE = "stacked";

    private String sequence;
    private String modifiedSequence;
    private Double intensity;
    private Double rawIntensity;
    private int intensityRank;
    private String intensityColor;
    private Double confidence;
    private Double rawConfidence;
    private int confidenceRank;
    private String confidenceColor;
    private String color;
    private String foregroundColor;
}
