package org.altusmetrum.altosuilib_14;

import com.sun.speech.freetts.Voice;

public final class AltosFlightVoice extends AltosVoice {
	private static final float FLIGHT_RATE = 145.0f;
	private static final float FLIGHT_PITCH = 95.0f;
	private static final float FLIGHT_PITCH_RANGE = 10.0f;

	public AltosFlightVoice() {
		super();

		Voice flightVoice = voice;
		if (flightVoice != null) {
			flightVoice.setRate(FLIGHT_RATE);
			flightVoice.setPitch(FLIGHT_PITCH);
			flightVoice.setPitchRange(FLIGHT_PITCH_RANGE);
		}
	}
}
