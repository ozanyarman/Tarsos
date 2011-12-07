package be.hogent.tarsos.util;

public class KernelDensityEstimate {
	private final double[] accumulator;
	private final Kernel kernel;

	public KernelDensityEstimate(final Kernel kernel, final int size) {
		accumulator = new double[size];
		this.kernel = kernel;
		if (kernel.size() > accumulator.length)
			throw new IllegalArgumentException(
					"The kernel size should be smaller than the acummulator size.");
	}

	/**
	 * Add the kernel to an accumulator for each value.
	 * 
	 * When a kernel with a width of 7 is added at 1 cents it has influence on
	 * the bins from 1200 - 7 * 10 + 1 to 1 + 7 * 10 so from 1131 to 71. To make
	 * the modulo calculation easy 1200 is added to each value: -69 % 1200 is
	 * -69, (-69 + 1200) % 1200 is the expected 1131. If you know what I mean.
	 * This algorithm computes O(width * n) sums with n the number of
	 * annotations and width the number of bins affected, rather efficient.
	 * 
	 * @param value
	 *            The value to add.
	 */
	public void add(double value) {
		int accumulatorSize = accumulator.length;
		int calculationAria = kernel.size() / 2;
		int start = (int) (value + accumulatorSize - calculationAria);
		int stop = (int) (value + accumulatorSize + calculationAria);
		if (kernel.size() % 2 != 0)
			stop++;
		int kernelIndex = 0;
		for (int i = start; i < stop; i++) {
			accumulator[i % accumulatorSize] += kernel.value(i - start);
			kernelIndex++;
		}
	}
	
	/**
	 * Remove a value from the kde, removes a kernel at the specified position.
	 * @param value The value to remove.
	 */
	public void remove(double value) {
		int accumulatorSize = accumulator.length;
		int calculationAria = kernel.size() / 2;
		int start = (int) (value + accumulatorSize - calculationAria);
		int stop = (int) (value + accumulatorSize + calculationAria);
		if (kernel.size() % 2 != 0)
			stop++;
		int kernelIndex = 0;
		for (int i = start; i < stop; i++) {
			accumulator[i % accumulatorSize] -= kernel.value(i - start);
			kernelIndex++;
		}
	}
	
	/**
	 * Shift the accumulator x positions.
	 * @param shift The number of positions the accumulator should be shifted.
	 */
	public void shift(int shift){
		double[] newValues = new double[size()];
		for(int index = 0 ; index < size() ; index++){
			newValues[index] = accumulator[(index + shift) % size()];
		}
		for(int index = 0 ; index < size() ; index++){
			accumulator[index] = newValues[index];
		}
	}

	/**
	 * Returns the current estimate.
	 * 
	 * @return The current estimate. To prevent unauthorized modification a
	 *         clone of the array is returned. Please cache appropriately.
	 */
	public double[] getEstimate() {
		return accumulator.clone();
	}

	/**
	 * Return the value for the accumulator at a certain index.
	 * 
	 * @param index
	 *            The index.
	 * @return The value for the accumulator at a certain index.
	 */
	public double getValue(final int index) {
		return accumulator[index];
	}

	/**
	 * @return The size of the accumulator.
	 */
	public int size() {
		return accumulator.length;
	}

	/**
	 * Returns the sum of all estimates in the accumulator.
	 * 
	 * @return The total sum of all estimates.
	 */
	public double getSumFreq() {
		double sum = 0.0;
		for (int i = 0; i < accumulator.length; i++) {
			sum += accumulator[i];
		}
		return sum;
	}

	/**
	 * Sets the maximum value in accumulator to 1.0
	 */
	public void normalize() {
		double maxElement = 0.0;
		for (int i = 0; i < size(); i++) {
			maxElement = Math.max(accumulator[i], maxElement);
		}

		if (maxElement > 0) {
			for (int i = 0; i < size(); i++) {
				accumulator[i] = accumulator[i] / maxElement;
			}
		}
	}
	
	/**
	 * Takes the maximum of the value in the accumulator for two kde's.
	 * @param other The other kde of the same size.
	 */
	public void max(KernelDensityEstimate other){
		assert other.size() == size() : "The kde size should be the same!";
		for (int i = 0; i < accumulator.length; i++) {
			accumulator[i] = Math.max(accumulator[i], other.accumulator[i]);
		}
	}
	
	/**
	 * Adds a KDE to this accumulator
	 * @param other The other KDE of the same size.
	 */
	public void add(KernelDensityEstimate other){
		assert other.size() == size() : "The kde size should be the same!";
		for (int i = 0; i < accumulator.length; i++) {
			accumulator[i] += other.accumulator[i];
		}
	}

	/**
	 * <p>
	 * Calculate a correlation with another KernelDensityEstimate. The index of
	 * the other estimates are shifted by a number which can be zero (or
	 * positive or negative). Beware: the index wraps around the edges.
	 * </p>
	 * <p>
	 * This and the other KernelDensityEstimate should have the same size.
	 * </p>
	 * 
	 * @param other
	 *            The other estimate.
	 * @param positionsToShiftOther
	 *            The number of positions to shift the estimate.
	 * @return A value between 0 and 1 representing how similar both estimates
	 *         are. 1 means total correlation, 0 no correlation.
	 */
	public double correlation(final KernelDensityEstimate other,
			final int positionsToShiftOther) {
		assert other.size() == size() : "The kde size should be the same!";
		double correlation;
		double matchingArea = 0.0;
		double biggestKDEArea = Math.max(getSumFreq(), other.getSumFreq());
		for (int i = 0; i < accumulator.length; i++) {
			int otherIndex = (i + positionsToShiftOther) % other.size();
			matchingArea += Math.min(accumulator[i],
					other.accumulator[otherIndex]);
		}
		if (matchingArea == 0.0) {
			correlation = 0.0;
		} else {
			correlation = matchingArea / biggestKDEArea;
		}
		return correlation;
	}

	/**
	 * Calculates how much the other KernelDensityEstimate needs to be shifted
	 * for optimal correlation.
	 * 
	 * @param other
	 *            The other KernelDensityEstimate.
	 * @return A number between 0 (inclusive) and the size of the
	 *         KernelDensityEstimate (exclusive) which represents how much the
	 *         other KernelDensityEstimate needs to be shifted for optimal
	 *         correlation.
	 */
	public int shiftForOptimalCorrelation(final KernelDensityEstimate other) {
		int optimalShift = 0; // displacement with best correlation
		double maximumCorrelation = -1; // best found correlation

		for (int shift = 0; shift < size(); shift++) {
			final double currentCorrelation = correlation(other, shift);
			if (maximumCorrelation < currentCorrelation) {
				maximumCorrelation = currentCorrelation;
				optimalShift = shift;
			}
		}

		return optimalShift;
	}

	/**
	 * Defines a kernel. It has a size and cached values for each index.
	 * 
	 * @author Joren Six
	 */
	public static interface Kernel {
		/**
		 * Fetch the value for the kernel at a certain index.
		 * 
		 * @param kernelIndex
		 *            The index of the previously computed value.
		 * @return The cached value for a certain index.
		 */
		double value(final int kernelIndex);

		/**
		 * The size of the kernel.
		 * 
		 * @return The size of the kernel.
		 */
		int size();
	}

	/**
	 * A Gaussian kernel function.
	 * 
	 * @author Joren Six
	 */
	public static class GaussianKernel implements Kernel {

		private final double kernel[];

		/**
		 * Construct a kernel with a defined width.
		 * 
		 * @param kernelWidth
		 *            The width of the kernel.
		 */
		public GaussianKernel(final double kernelWidth) {
			double calculationAria = 5 * kernelWidth;// Aria, not area
			double halfWidth = kernelWidth / 2.0;

			// Compute a kernel: a lookup table with e.g. a Gaussian curve
			kernel = new double[(int) calculationAria * 2 + 1];
			double difference = -calculationAria;
			for (int i = 0; i < kernel.length; i++) {
				double power = Math.pow(difference / halfWidth, 2.0);
				kernel[i] = Math.pow(Math.E, -0.5 * power);
				difference++;
			}
		}

		
		public double value(int index) {
			return kernel[index];
		}

		
		public int size() {
			return kernel.length;
		}
	}

	/**
	 * A rectangular kernel function.
	 */
	public static class RectangularKernel implements Kernel {

		private final double kernel[];

		public RectangularKernel(final double kernelWidth) {
			// Compute a kernel: a lookup table with a width
			kernel = new double[(int) kernelWidth];
			for (int i = 0; i < kernel.length; i++) {
				kernel[i] = 1.0;
			}
		}

		
		public double value(int index) {
			return kernel[index];
		}

		
		public int size() {
			return kernel.length;
		}
	}


}
