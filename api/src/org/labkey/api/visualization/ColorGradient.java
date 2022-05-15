package org.labkey.api.visualization;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/*
* inspired from https://github.com/matthewbeckler/HeatMap/blob/master/Gradient.java
* */
public class ColorGradient
{

    /**
     * Creates an array of Color objects for use as a gradient, using a linear
     * interpolation between the two specified colors.
     *
     * @param one      Color used for the bottom of the gradient
     * @param two      Color used for the top of the gradient
     * @param numSteps The number of steps in the gradient. 250 is a good number.
     */
    public static List<Color> createGradient(final Color one, final Color two, final int numSteps)
    {
        int r1 = one.getRed();
        int g1 = one.getGreen();
        int b1 = one.getBlue();
        int a1 = one.getAlpha();

        int r2 = two.getRed();
        int g2 = two.getGreen();
        int b2 = two.getBlue();
        int a2 = two.getAlpha();

        int newR = 0;
        int newG = 0;
        int newB = 0;
        int newA = 0;

        List<Color> gradient = new ArrayList<>();
        double iNorm;
        for (int i = 0; i < numSteps; i++)
        {
            iNorm = i / (double) numSteps; //a normalized [0:1] variable
            newR = (int) (r1 + iNorm * (r2 - r1));
            newG = (int) (g1 + iNorm * (g2 - g1));
            newB = (int) (b1 + iNorm * (b2 - b1));
            newA = (int) (a1 + iNorm * (a2 - a1));
            gradient.add(new Color(newR, newG, newB, newA));
        }

        return gradient;
    }


    public static float calculateLuminance(Color color)
    {
        return (float) (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue());
    }
    public static String getContrast(Color color)
    {
        float luminance = calculateLuminance(color);
        return (luminance < 140) ? "#fff" : "#000";
    }
}