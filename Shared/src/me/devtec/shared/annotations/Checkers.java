package me.devtec.shared.annotations;

public class Checkers {

	@Comment(comment = "Check if object is null. If it is, String in the Exception message will be used")
	public static void nonNull(Object object, String name) {
		if (object == null)
			throw new NullPointerException(name + " is used to be non-null, but is null.");
	}
}
