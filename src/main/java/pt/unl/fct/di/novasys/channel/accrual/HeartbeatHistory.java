package pt.unl.fct.di.novasys.channel.accrual;

import java.util.LinkedList;

/**
 * Based on the akka implementation of the Phi Accrual Failure Detector:
 * https://github.com/akka/akka/blob/main/akka-remote/src/main/scala/akka/remote/PhiAccrualFailureDetector.scala
 */

public class HeartbeatHistory {
    private final int maxSampleSize;
    private final long firstHeartbeatEstimateMs;

    private final LinkedList<Long> intervals;

    private long intervalSum;
    private long squaredIntervalSum;


    public HeartbeatHistory(int maxSampleSize, long firstHeartbeatEstimateMs) {
        this.maxSampleSize = maxSampleSize;
        this.firstHeartbeatEstimateMs = firstHeartbeatEstimateMs;

        intervals = new LinkedList<>();
        intervalSum = 0;
        squaredIntervalSum = 0;
    }

    public double mean() {
        return isEmpty() ? firstHeartbeatEstimateMs : (double) intervalSum / intervals.size();
    }

    public double stdDev() {
        return isEmpty() ? mean() / 4 : Math.sqrt(variance());
    }

    private double variance() {
        return ((double) squaredIntervalSum / intervals.size()) - (mean() * mean());
    }

    public void add(long interval) {
        if (intervals.size() >= maxSampleSize)
            dropOldest();
        intervals.addLast(interval);
        intervalSum += interval;
        squaredIntervalSum += (interval * interval);
    }

    private void dropOldest() {
        long oldest = intervals.removeFirst();
        intervalSum -= oldest;
        squaredIntervalSum -= (oldest * oldest);
    }

    public boolean isEmpty() {
        return intervals.isEmpty();
    }

    public int size(){
        return intervals.size();
    }


}
