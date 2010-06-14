package be.hogent.tarsos.pitch.pure;


/**
 * An implementation of the AUBIO_YIN pitch tracking algorithm. See <a href=
 * "http://recherche.ircam.fr/equipes/pcm/cheveign/ps/2002_JASA_YIN_proof.pdf"
 * >the AUBIO_YIN paper.</a> Implementation based on <a
 * href="http://aubio.org">aubio</a>
 * @author Joren Six
 */
public final class Yin implements PurePitchDetector {
    /**
     * The expected size of an audio buffer (in samples).
     */
    public static final int BUFFER_SIZE = 1024;

    /**
     * Overlap defines how much two audio buffers following each other should
     * overlap (in samples).
     */
    public static final int OVERLAP = 512;

    /**
     * The AUBIO_YIN threshold value. Should be around 0.10~0.15. See YIN paper
     * for more information.
     */
    private static final double THRESHOLD = 0.15;

    /**
     * The audio sample rate. Most audio has a sample rate of 44.1kHz.
     */
    private final float sampleRate;

    /**
     * The buffer that stores the calculated values. It is exactly half the size
     * of the input buffer.
     */
    private final float[] yinBuffer;

    public Yin(final float audioSampleRate) {
        this.sampleRate = audioSampleRate;
        yinBuffer = new float[BUFFER_SIZE / 2];
    }

    /**
     * The main flow of the AUBIO_YIN algorithm. Returns a pitch value in Hz or
     * -1 if no pitch is detected.
     * @return a pitch value in Hz or -1 if no pitch is detected.
     */
    public float getPitch(final float[] audioBuffer) {

        int tauEstimate = -1;
        float pitchInHertz = -1;

        // step 2
        difference(audioBuffer);

        // step 3
        cumulativeMeanNormalizedDifference();

        // step 4
        tauEstimate = absoluteThreshold();

        // step 5
        if (tauEstimate != -1) {
            final float betterTau = parabolicInterpolation(tauEstimate);

            // step 6
            // TODO Implement optimization for the AUBIO_YIN algorithm.
            // 0.77% => 0.5% error rate,
            // using the data of the AUBIO_YIN paper
            // bestLocalEstimate()

            // conversion to Hz
            pitchInHertz = sampleRate / betterTau;
        }

        return pitchInHertz;
    }

    /**
     * Implements the difference function as described in step 2 of the AUBIO_YIN
     * paper.
     */
    private void difference(final float[] audioBuffer) {
        int index, tau;
        float delta;
        for (tau = 0; tau < yinBuffer.length; tau++) {
            yinBuffer[tau] = 0;
        }
        for (tau = 1; tau < yinBuffer.length; tau++) {
            for (index = 0; index < yinBuffer.length; index++) {
                delta = audioBuffer[index] - audioBuffer[index + tau];
                yinBuffer[tau] += delta * delta;
            }
        }
    }

    /**
     * The cumulative mean normalized difference function as described in step 3
     * of the AUBIO_YIN paper. <br>
     * <code>
     * yinBuffer[0] == yinBuffer[1] = 1
     * </code>
     */
    private void cumulativeMeanNormalizedDifference() {
        int tau;
        yinBuffer[0] = 1;
        // Very small optimization in comparison with AUBIO
        // start the running sum with the correct value:
        // the first value of the yinBuffer
        float runningSum = yinBuffer[1];
        // yinBuffer[1] is always 1
        yinBuffer[1] = 1;
        // now start at tau = 2
        for (tau = 2; tau < yinBuffer.length; tau++) {
            runningSum += yinBuffer[tau];
            yinBuffer[tau] *= tau / runningSum;
        }
    }

    /**
     * Implements step 4 of the AUBIO_YIN paper.
     */
    private int absoluteThreshold() {
        // Uses another loop construct
        // than the AUBIO implementation
        int tau = 1;
        for (tau = 1; tau < yinBuffer.length; tau++) {
            if (yinBuffer[tau] < Yin.THRESHOLD) {
                while (tau + 1 < yinBuffer.length && yinBuffer[tau + 1] < yinBuffer[tau]) {
                    tau++;
                }
                // found tau, exit loop and return
                break;
            }
        }

        // if no pitch found, tau => -1
        if (tau == yinBuffer.length || yinBuffer[tau] >= Yin.THRESHOLD) {
            tau = -1;
        }

        return tau;
    }

    /**
     * Implements step 5 of the AUBIO_YIN paper. It refines the estimated tau value
     * using parabolic interpolation. This is needed to detect higher
     * frequencies more precisely. See http://fizyka.umk.pl/nrbook/c10-2.pdf
     * @param tauEstimate
     *            the estimated tau value.
     * @return a better, more precise tau value.
     */
    private float parabolicInterpolation(final int tauEstimate) {
        float betterTau;
        float s0, s1, s2;
        final int x0 = (tauEstimate < 1) ? tauEstimate : tauEstimate - 1;
        final int x2 = (tauEstimate + 1 < yinBuffer.length) ? tauEstimate + 1 : tauEstimate;
        if (x0 == tauEstimate) {
            betterTau = (yinBuffer[tauEstimate] <= yinBuffer[x2]) ? tauEstimate : x2;
        } else if (x2 == tauEstimate) {
            betterTau = (yinBuffer[tauEstimate] <= yinBuffer[x0]) ? tauEstimate : x0;
        } else {
            s0 = yinBuffer[x0];
            s1 = yinBuffer[tauEstimate];
            s2 = yinBuffer[x2];
            // fixed AUBIO implementation, thanks to Karl Helgason:
            // (2.0f * s1 - s2 - s0) was incorrectly multiplied with -1
            betterTau = tauEstimate + (s2 - s0) / (2 * (2 * s1 - s2 - s0));
        }
        return betterTau;
    }
}