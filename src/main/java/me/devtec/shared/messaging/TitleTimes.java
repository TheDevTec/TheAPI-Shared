package me.devtec.shared.messaging;

/**
 * Defines title display timings.
 *
 * <p>
 * Values are expressed in game ticks.
 * </p>
 */
public final class TitleTimes {

	public static final TitleTimes DEFAULT = new TitleTimes(10, 70, 20);

	private final int fadeIn;
	private final int stay;
	private final int fadeOut;

	public TitleTimes(int fadeIn, int stay, int fadeOut) {
		if (fadeIn < 0)
			throw new IllegalArgumentException("fadeIn");
		if (stay < 0)
			throw new IllegalArgumentException("stay");
		if (fadeOut < 0)
			throw new IllegalArgumentException("fadeOut");

		this.fadeIn = fadeIn;
		this.stay = stay;
		this.fadeOut = fadeOut;
	}

	public int fadeIn() {
		return fadeIn;
	}

	public int stay() {
		return stay;
	}

	public int fadeOut() {
		return fadeOut;
	}
}