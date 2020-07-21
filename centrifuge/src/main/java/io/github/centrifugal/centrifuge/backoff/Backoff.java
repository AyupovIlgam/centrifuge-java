package io.github.centrifugal.centrifuge.backoff;

import java.math.BigInteger;

public class Backoff {

    private long min = 100;
    private long max = 10000;
    private int factor = 2;
    private int attempts = 0;

    public Backoff() {
    }

    public void setMin(long min) {
        this.min = min;
    }

    public void setMax(long max) {
        this.max = max;
    }

    public void setFactor(int factor) {
        this.factor = factor;
    }

    public void reset() {
        attempts = 0;
    }

    public long duration() {
        BigInteger ms = BigInteger.valueOf(min).multiply(BigInteger.valueOf(factor).pow(attempts++));
        return ms.min(BigInteger.valueOf(max)).longValue();
    }
}
