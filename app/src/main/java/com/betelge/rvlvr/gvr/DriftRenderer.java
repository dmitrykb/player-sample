package com.betelge.rvlvr.gvr;

public interface DriftRenderer {

	int SIGNAL_TYPE_MONO = 0; // no signal splitting required before projection
	int SIGNAL_TYPE_STEREO_SIDE_BY_SIDE = 1; // split signal for left + right eye vertically before projection
	int SIGNAL_TYPE_STEREO_OVER_UNDER = 2; // OPTIONAL: split signal for left + right eye horizontally (left eye top) before projection

	int PROJECTION_TYPE_VR = 0;
	int PROJECTION_TYPE_NOVR = 1;

	int COLORSPACE_NV12 = 0;
	int COLORSPACE_YUY2 = 1;


	/**
	 *
	 * @param w - input signal horizontal resolution
	 * @param h - input signal vertical resolution
	 */
	void setResolution(int w, int h);

	/**
	 * Sets input format for YUV -> RGB converter
	 * @param format
	 */
	void setColorspace(int format);

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
	 * Set horizontal projection angle (sphere or partial sphere)
	 	@params: [0, 360]
	 */
	void setProjectionAngle(int projectionAngle);

	/**
	 * Set vertical projection angle
	 	@params: [0, 180]
	 */
	void setProjectionYAngle(int projectionYAngle);

	/**
	 * VR view or non-VR view
	 	@params: PROJECTION_TYPE_VR - vr mode, PROJECTION_TYPE_NOVR - non vr mode
	 */
	void setProjectionType(int projectionType); // PROJECTION_TYPE_VR, PROJECTION_TYPE_NOVR

	/**
	 * Full Screen mode (No Projection)
	 	@true - we don’t want to wrap the signal into a sphere and just render it as is, @false - wrap a signal into a sphere
	 */
	void setNoWrap(boolean noWrap);
}
