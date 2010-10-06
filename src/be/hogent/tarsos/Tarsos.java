package be.hogent.tarsos;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.UIManager;

import be.hogent.tarsos.cli.AbstractTarsosApp;
import be.hogent.tarsos.cli.Annotate;
import be.hogent.tarsos.cli.AnnotationSynth;
import be.hogent.tarsos.cli.AudioToScala;
import be.hogent.tarsos.cli.DetectPitch;
import be.hogent.tarsos.cli.MidiToWav;
import be.hogent.tarsos.cli.PitchTable;
import be.hogent.tarsos.cli.PitchToMidi;
import be.hogent.tarsos.cli.PowerExtractor;
import be.hogent.tarsos.cli.Rank;
import be.hogent.tarsos.cli.TuneMidiSynth;
import be.hogent.tarsos.ui.pitch.Frame;
import be.hogent.tarsos.util.Configuration;

/**
 * This is the starting point of the Tarsos application suite. It's main task is
 * to start other applications and to maintain state. Creating the needed
 * directories, checking the runtime, configure logging... also passing, minimal
 * parsing and checking arguments are tasks for this class.
 * 
 * @author Joren Six
 */
public final class Tarsos {

	/**
	 * A map of applications, maps the name of the application to the instance.
	 */
	private final transient Map<String, AbstractTarsosApp> applications;

	/**
	 * Properties file that defines the logging behavior.
	 */
	private static final String LOG_PROPS = "/be/hogent/tarsos/util/logging.properties";

	/**
	 * A reconfigurable logger.
	 */
	private Logger log;

	/**
	 * Create a new Tarsos application instance.
	 */
	private Tarsos() {
		// Configure logging.
		configureLogging();
		// Configure directories.
		configureDirectories();
		// Initialize the CLI application list.
		applications = new HashMap<String, AbstractTarsosApp>();
	}

	private void configureDirectories() {
		// Check and/or create the required directories.
		Configuration.createRequiredDirectories();

	}

	/**
	 * Configure the logging facility.
	 */
	private void configureLogging() {
		// a default (not configured) logger
		log = Logger.getLogger(Tarsos.class.getName());
		try {
			final InputStream stream = Tarsos.class.getResourceAsStream(LOG_PROPS);
			LogManager.getLogManager().readConfiguration(stream);
			// a configured logger
			log = Logger.getLogger(Tarsos.class.getName());
		} catch (final SecurityException e) {
			log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} catch (final IOException e) {
			log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Register a Tarsos application.
	 * 
	 * @param name
	 *            The name (parameter) of the Tarsos application. The parameter
	 *            is used in <code>java -jar tarsos.jar application</code>.
	 * @param application
	 *            The instance that represents the Tarsos application.
	 */
	public void registerApplication(final String name, final AbstractTarsosApp application) {
		applications.put(name, application);
	}

	/**
	 * @param arguments
	 *            The arguments for the program.
	 */
	public void run(final String... arguments) {
		if (arguments.length == 0) {
			startUserInterface();
		} else {
			startCommandLineApplication(arguments);
		}
	}

	private void startUserInterface() {
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception e) {
					log.log(Level.WARNING, "Unable to set system L&F, continue with default L&F", e);
				}
				final Frame frame = new Frame();
				frame.setVisible(true);
			}
		});

	}

	/**
	 * Starts a command line application.
	 * 
	 * @param arguments
	 *            The command line arguments.
	 */
	private void startCommandLineApplication(final String... arguments) {

		registerApplications();

		final String subcommand = arguments[0];
		String[] subcommandArgs;
		if (arguments.length > 1) {
			subcommandArgs = new String[arguments.length - 1];
			for (int i = 1; i < arguments.length; i++) {
				subcommandArgs[i - 1] = arguments[i];
			}
		} else {
			subcommandArgs = new String[0];
		}
		if (applications.containsKey(subcommand)) {
			applications.get(subcommand).run(subcommandArgs);
		} else {
			print("Unknown subcommand. Valid subcommands:");
			for (final String key : applications.keySet()) {
				print("\t" + key);
			}
		}
	}

	/**
	 * Registers a list of Tarsos CLI applications.
	 */
	private void registerApplications() {
		final List<AbstractTarsosApp> applicationList = new ArrayList<AbstractTarsosApp>();
		applicationList.add(new Annotate());
		applicationList.add(new PitchTable());
		applicationList.add(new MidiToWav());
		applicationList.add(new AudioToScala());
		applicationList.add(new DetectPitch());
		applicationList.add(new AnnotationSynth());
		applicationList.add(new PowerExtractor());
		applicationList.add(new TuneMidiSynth());
		applicationList.add(new Rank());
		applicationList.add(new PitchToMidi());
		for (final AbstractTarsosApp application : applicationList) {
			registerApplication(application.name(), application);
		}
	}

	/**
	 * The only instance of Tarsos.
	 */
	private static Tarsos tarsosInstance;

	/**
	 * Thread safe singleton implementation.
	 * 
	 * @return The only instance of the Tarsos application.
	 */
	public static Tarsos getInstance() {
		synchronized (Tarsos.class) {
			if (tarsosInstance == null) {
				tarsosInstance = new Tarsos();
			}
		}
		return tarsosInstance;
	}

	/**
	 * Prints information to standard out.
	 * 
	 * @param info
	 *            the information to print.
	 */
	private void print(final String info) {
		final PrintStream standardOut = System.out;
		standardOut.println(info);
	}

	/**
	 * Starts Tarsos. A command line application is started when command line
	 * arguments are present, otherwise the UI is used.
	 * 
	 * @param args
	 *            The arguments consist of a subcommand and options for the
	 *            subcommand. E.g.
	 * 
	 *            <pre>
	 * java -jar annotate --in blaat.wav
	 * java -jar annotate -bufferCount blaat.wav
	 * </pre>
	 */
	public static void main(final String... args) {
		final Tarsos instance = Tarsos.getInstance();
		instance.run(args);
	}

	/**
	 * Prints info to a stream (console).
	 * 
	 * @param info
	 *            The information to print.
	 */
	public static void println(final String info) {
		getInstance().print(info);
	}
}
