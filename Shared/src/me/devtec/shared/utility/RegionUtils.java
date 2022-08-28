package me.devtec.shared.utility;

public class RegionUtils {
	public static boolean isInside(double pointX, double pointY, double pointZ, double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
		double xMin = Math.min(fromX, toX);
		double yMin = Math.min(fromY, toY);
		double zMin = Math.min(fromZ, toZ);
		double xMax = Math.max(fromX, toX);
		double yMax = Math.max(fromY, toY);
		double zMax = Math.max(fromZ, toZ);
		return pointX >= xMin && pointX <= xMax && pointY >= yMin && pointY <= yMax && pointZ >= zMin && pointZ <= zMax;
	}

	public static boolean isInside(double[] points, double[] from, double[] to) {
		return isInside(points[0], points[1], points[2], from[0], from[1], from[2], to[0], to[1], to[2]);
	}
}
