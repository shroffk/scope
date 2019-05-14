package org.epics.pvaccess.scope;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

public class SignalGenerator {

    public interface Signal {
        double[] nextListDouble(Instant instant);
    }

    /**
     * Creates a triangle wave of nSamples samples, with given period and given
     * wavelength of given number of samples, updating at given rate.
     *
     * @param periodInSeconds
     *            the period measured in seconds
     * @param wavelengthInSamples
     *            the wavelength measured in samples
     * @param nSamples
     *            the number of samples
     * @param updateRateInSeconds
     *            the update rate in seconds
     */
    public static Signal generateSawtoothWaveform(Double periodInSeconds, Double wavelengthInSamples, Double nSamples,
            Double updateRateInSeconds) {
        return new SawtoothWaveform(periodInSeconds, wavelengthInSamples, nSamples, updateRateInSeconds);
    }

    /**
     * Creates sine wave of nSamples samples, with given period and given wavelength
     * of given number of samples, updating at given rate.
     *
     * @param periodInSeconds
     *            the period measured in seconds
     * @param wavelengthInSamples
     *            the wavelength measured in samples
     * @param nSamples
     *            the number of samples
     * @param updateRateInSeconds
     *            the update rate in seconds
     */
    public static Signal generateSineWaveform(Double periodInSeconds, Double wavelengthInSamples, Double nSamples, Double updateRateInSeconds) {
        return new SineWaveform(periodInSeconds, wavelengthInSamples, nSamples, updateRateInSeconds);
    }

    /**
     * Creates a gaussian wave of given number of samples, with given period and
     * standard, updating at the given rate
     *
     * @param periodInSeconds
     *            the period measured in seconds
     * @param stdDev
     *            standard deviation of the gaussian distribution
     * @param nSamples
     *            number of elements in the waveform
     * @param updateRateInSeconds
     *            time between samples in seconds
     */
    public static Signal generateGaussianWaveform(Double periodInSeconds, Double stdDev, Double nSamples,
            Double updateRateInSeconds) {
        return new GaussianWaveform(periodInSeconds, stdDev, nSamples, updateRateInSeconds);
    }

    /**
     * Creates a square wave of nSamples samples, with given period and given
     * wavelength of given number of samples, updating at given rate.
     *
     * @param periodInSeconds
     *            the period measured in seconds
     * @param wavelengthInSamples
     *            the wavelength measured in samples
     * @param nSamples
     *            the number of samples
     * @param updateRateInSeconds
     *            the update rate in seconds
     */
    public static Signal generateSquareWaveform(Double periodInSeconds, Double wavelengthInSamples, Double nSamples,
            Double updateRateInSeconds) {
        return new SquareWaveform(periodInSeconds, wavelengthInSamples, nSamples, updateRateInSeconds);
    }
    
    /**
     * Creates a noise waveform signal with a gaussian distribution, updating at the
     * rate specified.
     *
     * @param min
     *            the minimum value
     * @param max
     *            the maximum value
     * @param nSamples
     *            number of elements in the waveform
     * @param interval
     *            time between samples in seconds
     */
    public static Signal generateNoiseWaveform(Double min, Double max, Double nSamples, Double interval) {
        return new NoiseWaveform(min, max, nSamples, interval);
    }
    static class SawtoothWaveform implements Signal {
        private Instant initialReference = Instant.now();
        private double omega;
        private double k;
        private int nSamples;

        /**
         * Creates a triangle wave of nSamples samples, with given period and given
         * wavelength of given number of samples, updating at given rate.
         *
         * @param periodInSeconds
         *            the period measured in seconds
         * @param wavelengthInSamples
         *            the wavelength measured in samples
         * @param nSamples
         *            the number of samples
         * @param updateRateInSeconds
         *            the update rate in seconds
         */
        public SawtoothWaveform(Double periodInSeconds, Double wavelengthInSamples, Double nSamples,
                Double updateRateInSeconds) {

            this.omega = 2 * Math.PI / periodInSeconds;
            this.k = 2 * Math.PI / wavelengthInSamples;
            this.nSamples = nSamples.intValue();
            if (this.nSamples <= 0) {
                throw new IllegalArgumentException("Number of sample must be a positive integer.");
            }
        }

        @Override
        public double[] nextListDouble(Instant instant) {

            Duration difference = Duration.between(initialReference, instant);
            double time = difference.getSeconds() + difference.getNano() / 1000000000.0;

            double[] newArray = new double[nSamples];
            for (int i = 0; i < newArray.length; i++) {
                double x = (omega * time + k * i) / (2 * Math.PI);
                double normalizedPositionInPeriod = x - (double) (long) x;
                newArray[i] = -1.0 + 2 * normalizedPositionInPeriod;
            }
            return newArray;
        }
    }

    static class GaussianWaveform implements Signal {
        private Instant initialReference = Instant.now();
        private final double omega;
        private double[] buffer;

        /**
         * Creates a gaussian wave of given number of samples, with given period and
         * standard, updating at the given rate
         *
         * @param periodInSeconds
         *            the period measured in seconds
         * @param stdDev
         *            standard deviation of the gaussian distribution
         * @param nSamples
         *            number of elements in the waveform
         * @param updateRateInSeconds
         *            time between samples in seconds
         */
        public GaussianWaveform(Double periodInSeconds, Double stdDev, Double nSamples, Double updateRateInSeconds) {

            int size = nSamples.intValue();
            this.omega = 2 * Math.PI / periodInSeconds;
            buffer = new double[size];
            populateGaussian(buffer, stdDev);
        }

        void populateGaussian(double[] array, double stdDev) {
            for (int i = 0; i < array.length; i++) {
                array[i] = gaussian(i, array.length / 2.0, stdDev);
            }
        }

        /**
         * 1D gaussian, centered on centerX and with the specified width.
         * 
         * @param x
         *            coordinate x
         * @param centerX
         *            center of the gaussian on x
         * @param width
         *            width of the gaussian in all directions
         * @return the value of the function at the given coordinates
         */
        public double gaussian(double x, double centerX, double width) {
            return Math.exp((-Math.pow((x - centerX), 2.0)) / width);
        }

        @Override
        public double[] nextListDouble(Instant instant) {

            Duration difference = Duration.between(initialReference, instant);
            double time = difference.getSeconds() + difference.getNano() / 1000000000.0;

            double x = time * omega / (2 * Math.PI);
            double normalizedX = x - (double) (long) x;
            int offset = (int) (normalizedX * buffer.length);
            if (offset == buffer.length) {
                offset = 0;
            }
            int localCounter = offset;
            double[] newArray = new double[buffer.length];
            for (int i = 0; i < newArray.length; i++) {
                newArray[i] = buffer[localCounter];
                localCounter++;
                if (localCounter >= buffer.length) {
                    localCounter -= buffer.length;
                }
            }
            return newArray;
        }
    }

    static class SineWaveform implements Signal {
        private Instant initialReference = Instant.now();
        private final double omega;
        private final double k;
        private int nSamples;

        /**
         * Creates sine wave of nSamples samples, with given period and given wavelength of
         * given number of samples, updating at given rate.
         *
         * @param periodInSeconds the period measured in seconds
         * @param wavelengthInSamples the wavelength measured in samples
         * @param nSamples the number of samples
         * @param updateRateInSeconds the update rate in seconds
         */
        public SineWaveform(Double periodInSeconds, Double wavelengthInSamples, Double nSamples, Double updateRateInSeconds) {
            this.omega = 2 * Math.PI / periodInSeconds;
            this.k = 2 * Math.PI / wavelengthInSamples;
            this.nSamples = nSamples.intValue();
            if (this.nSamples <= 0) {
                throw new IllegalArgumentException("Number of sample must be a positive integer.");
            }
        }

        @Override
        public double[] nextListDouble(Instant instant) {

            Duration difference = Duration.between(initialReference, instant);
            double time = difference.getSeconds() + difference.getNano() / 1000000000.0;

            double[] newArray = new double[nSamples];
            for (int i = 0; i < newArray.length; i++) {
                newArray[i] = Math.sin(omega * time + k * i);
            }
            return newArray;
        }
    }

    static class SquareWaveform implements Signal {
        private Instant initialReference = Instant.now();
        private final double omega;
        private final double k;
        private int nSamples;

        /**
         * Creates a square wave of nSamples samples, with given period and given wavelength of
         * given number of samples, updating at given rate.
         *
         * @param periodInSeconds the period measured in seconds
         * @param wavelengthInSamples the wavelength measured in samples
         * @param nSamples the number of samples
         * @param updateRateInSeconds the update rate in seconds
         */
        public SquareWaveform(Double periodInSeconds, Double wavelengthInSamples, Double nSamples, Double updateRateInSeconds) {
            this.omega = 2 * Math.PI / periodInSeconds;
            this.k = 2 * Math.PI / wavelengthInSamples;
            this.nSamples = nSamples.intValue();
            if (this.nSamples <= 0) {
                throw new IllegalArgumentException("Number of sample must be a positive integer.");
            }
        }

        @Override
        public double[] nextListDouble(Instant instant) {

            Duration difference = Duration.between(initialReference, instant);
            double time = difference.getSeconds() + difference.getNano() / 1000000000.0;

            double[] newArray = new double[nSamples];
            for (int i = 0; i < newArray.length; i++) {
                double x = (omega * time + k * i) / (2 * Math.PI);
                double normalizedPositionInPeriod = x - (double) (long) x;
                if (normalizedPositionInPeriod < 0.5) {
                    newArray[i] = 1.0;
                } else if (normalizedPositionInPeriod < 1.0) {
                    newArray[i] = -1.0;
                } else {
                    newArray[i] = 1.0;
                }
            }
            return newArray;
        }
    }

    static class NoiseWaveform implements Signal {
        private Instant initialReference = Instant.now();
        private Random rand = new Random();
        private int nSamples;
        private Double range;
        private Double min;

        /**
         * simulate a waveform containing a uniformly distributed random data.
         *
         * @param min
         *            the minimum value
         * @param max
         *            the maximum value
         * @param nSamples
         *            number of elements in the waveform
         * @param interval
         *            time between samples in seconds
         */
        public NoiseWaveform(Double min, Double max, Double nSamples, Double interval) {
            this.min = min;
            this.range = max - min;
            this.nSamples = nSamples.intValue();
            if (this.nSamples <= 0) {
                throw new IllegalArgumentException("Number of sample must be a positive integer.");
            }
        }

        @Override
        public double[] nextListDouble(Instant instant) {

            Duration difference = Duration.between(initialReference, instant);
            double time = difference.getSeconds() + difference.getNano() / 1000000000.0;
            double[] newArray = new double[nSamples];
            
            for (int i = 0; i < newArray.length; i++) {
                newArray[i] = (rand.nextGaussian() *range/2) + (min + range/2);
            }
            return newArray;
        }
    }
}
