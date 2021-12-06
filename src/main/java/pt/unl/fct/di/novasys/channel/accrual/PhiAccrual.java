package pt.unl.fct.di.novasys.channel.accrual;

import java.util.HashMap;
import java.util.Map;

/**
 * Based on the akka implementation of the Phi Accrual Failure Detector:
 * https://github.com/akka/akka/blob/main/akka-remote/src/main/scala/akka/remote/PhiAccrualFailureDetector.scala
 */

public class PhiAccrual {

    private final HeartbeatHistory history;
    private final double threshold;
    private final int minStdDeviationMs;
    private final int acceptableHbPauseMs;
    private long lastHb;


    public PhiAccrual(int windowSize, double threshold, int minStdDeviationMs,
                      int acceptableHbPauseMs, int firstHbEstimateMs) {
        lastHb = 0;
        this.history = new HeartbeatHistory(windowSize, firstHbEstimateMs);
        this.acceptableHbPauseMs = acceptableHbPauseMs;
        this.minStdDeviationMs = minStdDeviationMs;
        //Threshold <= 0 means always available, and we leave fail detection to the upper layer
        this.threshold = threshold;
    }

    public void receivedHb(long hbCounter) {
        long timestamp = System.currentTimeMillis();
        //If lastHb == 0, it is the first hb and do nothing as we need 2 hbs to calculate first interval
        if (lastHb != 0) {
            long interval = timestamp - lastHb;
            // don't use the first heartbeat after failure for the history, since a long pause will skew the stats
            //if(isAvailable(timestamp))
                history.add(interval);
        }
        lastHb = timestamp;
    }

    public boolean isAvailable(long timestamp){
        //Threshold <= 0 means always available, and we leave fail detection to the upper layer
        return threshold <= 0 || phi(timestamp).get("phi") < threshold;
    }

    public Map<String, Double> phi(long timestamp) {
        Map<String, Double> res = new HashMap<>();
        if (lastHb == 0) {
            res.put("timeDiff", 0.0);
            res.put("mean", 0.0);
            res.put("stdDev", 0.0);
            res.put("phi", 0.0);
        } else {
            long timeDiff = timestamp - lastHb;
            double mean = history.mean() + acceptableHbPauseMs;
            double stdDev = Math.max(history.stdDev(), minStdDeviationMs);
            res.put("timeDiff", (double) timeDiff);
            res.put("mean", mean);
            res.put("stdDev", stdDev);
            res.put("phi", phi(timeDiff, mean, stdDev));
        }
        return res;
    }

    /**
     * Following lines are quoted from Akka implementation:
     * https://github.com/akka/akka/blob/main/akka-remote/src/main/scala/akka/remote/PhiAccrualFailureDetector.scala
     * <p>
     * Calculation of phi, derived from the Cumulative distribution function for
     * N(mean, stdDeviation) normal distribution, given by
     * 1.0 / (1.0 + math.exp(-y * (1.5976 + 0.070566 * y * y)))
     * where y = (x - mean) / standard_deviation
     * This is an approximation defined in β Mathematics Handbook (Logistic approximation).
     * Error is 0.00014 at +- 3.16
     * The calculated value is equivalent to -log10(1 - CDF(y))
     */
    private double phi(long timeDiff, double mean, double stdDev) {
        double y = (timeDiff - mean) / stdDev;
        double e = Math.exp(-y * (1.5976 + 0.070566 * y * y));

        if (timeDiff > mean) {
            return -Math.log10(e / (1.0 + e));
        } else {
            return -Math.log10(1.0 - 1.0 / (1.0 - e));
        }
    }

}
