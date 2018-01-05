package com.betelge.rvlvr.gvr;

public interface DriftRenderer {

	int SIGNAL_TYPE_MONO = 0; // no signal splitting required before projection
	int SIGNAL_TYPE_STEREO_SIDE_BY_SIDE = 1; // split signal for left + right eye vertically before projection
	int SIGNAL_TYPE_STEREO_OVER_UNDER = 2; // OPTIONAL: split signal for left + right eye horizontally (left eye top) before projection

	int PROJECTION_TYPE_VR = 0;
	int PROJECTION_TYPE_NOVR = 1;


	/**
	 *
	 * @param w - input signal horizontal resolution
	 * @param h - input signal vertical resolution
	 */
	void setResolution(int w, int h);

	/**
	 	Crop vertical axis of incoming signal prior to projection by aspect ratio
	 */
	void setSignalAspectRatio(int w, int h);


	/**
	 * Split signal prior to projection
	 	@params: SIGNAL_TYPE_MONO, SIGNAL_TYPE_STEREO_SIDE_BY_SIDE or SIGNAL_TYPE_STEREO_OVER_UNDER
	 */
	void setSignalType(int stereotype);

	/**
	 * Set projection angle (sphere or partial sphere)
	 	@params: for now only 360 or 180
	 */
	void setProjectionAngle(int projectionAngle);

	/**
	 * VR view or non-VR view
	 	@params: PROJECTION_TYPE_VR - vr mode, PROJECTION_TYPE_NOVR - non vr mode
	 */
	void setProjectionType(int projectionType); // PROJECTION_TYPE_VR, PROJECTION_TYPE_NOVR

	/**
	 * Full Screen mode (No Projection)
	 	@true - we don’t want to wrap the signal into a sphere and just render it as is, @false - wrap a signal into a sphere
	 */
	void setNoWrap(boolean b);
}
