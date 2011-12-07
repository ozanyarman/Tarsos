package be.hogent.tarsos.sampled.pitch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import be.hogent.tarsos.sampled.AudioDispatcher;
import be.hogent.tarsos.sampled.AudioProcessor;
import be.hogent.tarsos.util.AudioFile;
import be.hogent.tarsos.util.ConfKey;
import be.hogent.tarsos.util.Configuration;
import be.hogent.tarsos.util.SignalPowerExtractor;

/**
 * @author Joren Six
 */
public final class TarsosPitchDetection implements PitchDetector {

	/**
	 * Logs exceptions.
	 */
	private static final Logger LOG = Logger.getLogger(TarsosPitchDetection.class.getName());

	/**
	 * The file to process.
	 */
	private final AudioFile file;
	/**
	 * A list of annotations.
	 */
	private final List<Annotation> annotations;

	/**
	 * Which pitch detector to use.
	 */
	private final PitchDetectionMode detectionMode;


	public TarsosPitchDetection(final AudioFile audioFile, final PitchDetectionMode pitchDetectionMode) {
		this.file = audioFile;
		this.annotations = new ArrayList<Annotation>();
		this.detectionMode = pitchDetectionMode;
		audioFile.getLengthInMilliSeconds();
	}

	public List<Annotation> executePitchDetection() {
		try {
			processFile(new AnnotationHandler() {
				public void handleAnnotation(final Annotation annotation) {
					annotations.add(annotation);
				}
			});
		} catch (final UnsupportedAudioFileException e) {
			LOG.log(Level.SEVERE, "Unsupported audio file: " + file.originalBasename() + " " + e.getMessage(), e);
		} catch (final IOException e) {
			LOG.log(Level.SEVERE,
					"Exception while reading audio file: " + file.originalBasename() + " " + e.getMessage(), e);
		}
		return annotations;
	}

	public String getName() {
		return detectionMode.getParametername();
	}

	public List<Annotation> getAnnotations() {
		return annotations;
	}

	/**
	 * Annotate a file with pitch information.
	 * 
	 * @param fileName
	 *            the file to annotate.
	 * @param detectedPitchHandler
	 *            handles the pitch information.
	 * @param detectionMode
	 * 			Defines the detection mode used.
	 * @throws UnsupportedAudioFileException
	 *             Currently only WAVE files with one channel (MONO) are
	 *             supported.
	 * @throws IOException
	 *             If there is an error reading the file.
	 */
	public static void processFile(final String fileName, final PitchDetectionMode detectionMode,
			final AnnotationHandler detectedPitchHandler) throws UnsupportedAudioFileException, IOException {
		final AudioInputStream ais = AudioSystem.getAudioInputStream(new File(fileName));
		processStream(ais, detectedPitchHandler, detectionMode);
	}
	
	private void processFile(final AnnotationHandler detectedPitchHandler) throws UnsupportedAudioFileException, IOException {
		final AudioInputStream ais = AudioSystem.getAudioInputStream(new File(file.transcodedPath()));
		final float sampleRate = ais.getFormat().getSampleRate();
		final long totalSamples = (long) (file.getLengthInMilliSeconds()/1000.0*sampleRate);
		processStream(ais, detectedPitchHandler, detectionMode,this,totalSamples);
	}

	/**
	 * Annotate an audio stream: useful for real-time pitch tracking.
	 * 
	 * @param ais
	 *            The audio input stream.
	 * @param detectedPitchHandler
	 *            Handles the pitch information.
	 * @param detectionMode
	 * 			Defines the detection mode used.
	 * @throws UnsupportedAudioFileException
	 *             Currently only WAVE streams with one channel (MONO) are
	 *             supported.
	 * @throws IOException
	 *             If there is an error reading the stream.
	 */
	public static void processStream(final AudioInputStream ais,
			final AnnotationHandler detectedPitchHandler, final PitchDetectionMode detectionMode)
			throws UnsupportedAudioFileException, IOException {
		processStream(ais, detectedPitchHandler, detectionMode,null,0);
	}
	
	private static void processStream(final  AudioInputStream ais,
	final AnnotationHandler detectedPitchHandler, final PitchDetectionMode detectionMode,final TarsosPitchDetection detector,final long totalSamples)
	throws UnsupportedAudioFileException, IOException{
		final float sampleRate = ais.getFormat().getSampleRate();
		final PurePitchDetector pureDetector;
		final int bufferSize;
		final int overlapSize;

		if (PitchDetectionMode.TARSOS_MPM == detectionMode) {
			pureDetector = new McLeodPitchMethod(sampleRate);
			bufferSize = McLeodPitchMethod.DEFAULT_BUFFER_SIZE;
			overlapSize = McLeodPitchMethod.DEFAULT_OVERLAP;
		} else if (PitchDetectionMode.TARSOS_FAST_YIN == detectionMode) {
				pureDetector = new Yin(sampleRate, 256,0.75);
				bufferSize =  256;
				overlapSize = 0;
		} else {
			pureDetector = new Yin(sampleRate, Yin.DEFAULT_BUFFER_SIZE);
			bufferSize = Yin.DEFAULT_BUFFER_SIZE;
			overlapSize = Yin.DEFAULT_OVERLAP;
		}

		final int bufferStepSize = bufferSize - overlapSize;

		final AudioDispatcher dispatcher = new AudioDispatcher(ais, bufferSize, overlapSize);
		dispatcher.addAudioProcessor(new AudioProcessor() {
			private long samplesProcessed = 0;

			public void processFull(final float[] audioFloatBuffer, final byte[] audioByteBuffer) {
				samplesProcessed += audioFloatBuffer.length;
				if(detector != null){
					detector.setProgress(samplesProcessed/(float)totalSamples);
				}
				processBuffer(audioFloatBuffer);
			}

			public void processOverlapping(final float[] audioFloatBuffer, final byte[] audioByteBuffer) {
				samplesProcessed += bufferStepSize;
				if(detector != null){
					detector.setProgress(samplesProcessed/(float)totalSamples);
				}
				processBuffer(audioFloatBuffer);
			}

			private void processBuffer(final float[] audioFloatBuffer) {
				boolean isSilence = SignalPowerExtractor.isSilence(audioFloatBuffer);
				// Do not detect pitch on silence.
				if (!isSilence) {
					final float pitch = pureDetector.getPitch(audioFloatBuffer);
					// The pure pitch detectors return -1 when no pitch is
					// detected. Creating an annotation without pitch is not
					// useful.
					boolean isPitched = pitch != -1;
					// The pitch detectors should not return 0 Hz.
					assert pitch != 0;
					if (isPitched) {
						final float time = samplesProcessed / sampleRate;
						final float probability = pureDetector.getProbability();
						final Annotation annotation = new Annotation(time, pitch, detectionMode, probability);
						detectedPitchHandler.handleAnnotation(annotation);
					}
				}
			}

			public void processingFinished() {
			}
		});
		// in live mode start the thread in background
		if (Configuration.getBoolean(ConfKey.tarsos_live)) {
			new Thread(dispatcher, "Annotation publisher").start();
		} else {
			// in analysis mode: foreground
			new Thread(dispatcher, "Annotation publisher").run();
		}
	}
	
	double progress = 0;
	private void setProgress(final double progress){
		this.progress = progress;
	}

	
	public double progress() {
		return progress;
	}
}
