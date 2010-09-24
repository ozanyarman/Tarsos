package be.hogent.tarsos.cli.temp;

import java.util.Random;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import javax.swing.JFrame;

import be.hogent.tarsos.midi.LogReceiver;
import be.hogent.tarsos.midi.MidiCommon;
import be.hogent.tarsos.midi.MidiUtils;
import be.hogent.tarsos.midi.ReceiverSink;

public final class GervillDelayTest {

	private GervillDelayTest() {
	}

	public static void main(final String[] args) throws Exception {
		Receiver recv;
		final MidiDevice outputDevice = MidiCommon.chooseMidiDevice(false, true);
		outputDevice.open();
		recv = outputDevice.getReceiver();
		final MidiDevice midiInputDevice = MidiCommon.chooseMidiDevice(true, false);
		midiInputDevice.open();
		final Transmitter midiInputTransmitter = midiInputDevice.getTransmitter();

		recv = new ReceiverSink(true, recv, new LogReceiver());
		midiInputTransmitter.setReceiver(recv);

		final ShortMessage msg = new ShortMessage();

		final Random rnd = new Random();
		final double[] tunings = new double[128];
		for (int i = 1; i < 128; i++) {
			tunings[i] = i * 100 + rnd.nextDouble() * 400;
		}

		MidiUtils.sendTunings(recv, 0, 0, "test", tunings);
		MidiUtils.sendTuningChange(recv, 0, 0);
		msg.setMessage(ShortMessage.NOTE_ON, 0, 69, 100);
		recv.send(msg, -1);
		msg.setMessage(ShortMessage.NOTE_OFF, 0, 69, 0);
		recv.send(msg, -1);
		new JFrame().setVisible(true);
	}
}
