package com.example.projectnoise.util;
import java.util.*;

public class MovingAverage {
    // queue used to store list so that we get the average
    private final Queue<Double> Dataset = new LinkedList<Double>();
    private final int period;
    private double sum;

    // constructor to initialize period
    public MovingAverage(int period)
    {
        this.period = period;
    }

    // Function to add new data in the list, and update the sum so that we get the new mean
    public void addData(double num)
    {
        sum += num;
        Dataset.add(num);

        // Updating size so that length of data set is equal to the defined period
        if (Dataset.size() > period)
        {
            sum -= Dataset.remove();
        }
    }

    // Function to calculate mean
    public double getMean()
    {
        return sum / period;
    }
}
