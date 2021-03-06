/*
 * Copyright (c) 2008, Esko Luontola. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.orfjackal.tools;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author Esko Luontola
 * @since 30.1.2008
 */
public class Benchmark {

    private static final Runnable NULL_BENCHMARK = new Runnable() {
        public void run() {
        }
    };

    private final int measureRounds;
    private final int minimumDurationMs;
    private final List<Result> results = new ArrayList<Result>();

    public Benchmark() {
        this(5, 500);
    }

    public Benchmark(int measureRounds, int minimumDurationMs) {
        this.measureRounds = measureRounds;
        this.minimumDurationMs = minimumDurationMs;
    }

    public Result runBenchmark(String description, Runnable benchmark) {
        int repeats = autoRepeatCount(benchmark, minimumDurationMs);
        long[] durations = finalMeasurements(benchmark, repeats, measureRounds);
        long[] measurementNoise = finalMeasurements(NULL_BENCHMARK, repeats, measureRounds);
        Result result = new Result(description, repeats, durations, measurementNoise);
        results.add(result);
        return result;
    }

    private static int autoRepeatCount(Runnable benchmark, int minimumDurationMs) {
        int repeats = 1;
        long durationMs = 0;
        while (durationMs < minimumDurationMs) {
            long start = System.currentTimeMillis();
            repeat(repeats, benchmark);
            long end = System.currentTimeMillis();

            durationMs = end - start;
            if (durationMs < minimumDurationMs) {
                repeats = repeats * 2;
            }
        }
        return repeats;
    }

    private static long[] finalMeasurements(Runnable benchmark, int repeats, int measureRounds) {
        long[] durations = new long[measureRounds];
        for (int i = 0; i < durations.length; i++) {
            long start = System.currentTimeMillis();
            repeat(repeats, benchmark);
            long end = System.currentTimeMillis();
            durations[i] = end - start;
        }
        return durations;
    }

    private static void repeat(int repeats, Runnable benchmark) {
        for (int i = 0; i < repeats; i++) {
            benchmark.run();
        }
    }

    public void printResults() {
        DecimalFormat nf = getNumberFormat();
        int indexMaxLength = (results.size() + 1) / 10 + 1;
        int descMaxLength = 0;
        int nanosMaxLength = 0;
        for (Result result : results) {
            descMaxLength = Math.max(descMaxLength, result.getDescription().length());
            nanosMaxLength = Math.max(nanosMaxLength, nf.format(result.getMaxNanos()).length());
        }
        System.out.println("Benchmark results (" + measureRounds + " measurement rounds)");
        for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            String index = pad(i + 1, -indexMaxLength, " ");
            String desc = pad(result.getDescription(), descMaxLength, " ");
            String nanos = pad(nf.format(result.getMedianNanos()), -nanosMaxLength, " ");
            String minNanos = pad(nf.format(result.getMinNanos()), -nanosMaxLength, " ");
            String maxNanos = pad(nf.format(result.getMaxNanos()), -nanosMaxLength, " ");
            System.out.println(index + ": " + desc + "    " + nanos + " ns"
                    + "    (min " + minNanos + " ns, max " + maxNanos + " ns)");
        }
    }

    public void printRawData() {
        System.out.println("Raw benchmark data");
        for (int i = 0; i < results.size(); i++) {
            System.out.println((i + 1) + ": " + results.get(i));
        }
    }

    private static DecimalFormat getNumberFormat() {
        return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    private static String pad(Object str, int padlen, String pad) {
        String padding = "";
        int len = Math.abs(padlen) - str.toString().length();
        if (len < 1) {
            return str.toString();
        }
        for (int i = 0; i < len; ++i) {
            padding = padding + pad;
        }
        return (padlen < 0 ? padding + str : str + padding);
    }

    public static class Result {

        private static final int MILLIS_TO_NANOS = 1000 * 1000;

        private final String description;
        private final int repeats;
        private final long[] totalDurationsMs;
        private final long measurementNoiseMs;

        public Result(String description, int repeats, long[] totalDurationsMs, long[] measurementNoiseMs) {
            totalDurationsMs = sortedCopy(totalDurationsMs);
            measurementNoiseMs = sortedCopy(measurementNoiseMs);
            this.measurementNoiseMs = min(measurementNoiseMs);
            subtractNoise(totalDurationsMs, this.measurementNoiseMs);
            this.description = description;
            this.repeats = repeats;
            this.totalDurationsMs = totalDurationsMs;
        }

        private static long[] sortedCopy(long[] values) {
            values = values.clone();
            Arrays.sort(values);
            return values;
        }

        private static void subtractNoise(long[] totalMs, long noiseMs) {
            for (int i = 0; i < totalMs.length; i++) {
                totalMs[i] -= noiseMs;
            }
        }

        public String getDescription() {
            return description;
        }

        public int getRepeats() {
            return repeats;
        }

        public double getMedianNanos() {
            return toActualNanos(median(totalDurationsMs));
        }

        public double getMinNanos() {
            return toActualNanos(min(totalDurationsMs));
        }

        public double getMaxNanos() {
            return toActualNanos(max(totalDurationsMs));
        }

        private double toActualNanos(long millis) {
            return millis * ((double) MILLIS_TO_NANOS / (double) repeats);
        }

        private static long median(long[] longs) {
            return longs[longs.length / 2];
        }

        private static long min(long[] longs) {
            return longs[0];
        }

        private static long max(long[] longs) {
            return longs[longs.length - 1];
        }

        public String toString() {
            NumberFormat nf = getNumberFormat();
            return "Benchmark.Result[" + getDescription() + ": " + nf.format(getMedianNanos()) + " ns ("
                    + getRepeats() + " repeats, measurements: " + listMs(totalDurationsMs)
                    + "; subtracted " + measurementNoiseMs + " ms measurement noise)]";
        }

        private static String listMs(long[] milliseconds) {
            String result = "";
            for (long ms : milliseconds) {
                if (result.length() > 0) {
                    result += ", ";
                }
                result += ms + " ms";
            }
            return result;
        }
    }
}
