package extras;

import java.util.Random;

public class UcsModel {
	/* campaign attributes as set by server */
	/*
	 * The current bid and targetted percentile for the user classification
	 * service
	 */
	private Random random;

	private double ucsBid;
	private double ucsBidPercentile;

	// logistic regression parameters
	private double ucsLearningRate = 0.3;
	private double ucsAlpha;
	private double ucsBeta;

	// latest reported level level and cost, to be applicable during the
	// following simulation day
	@SuppressWarnings("unused")
	private double ucsLevel;
	@SuppressWarnings("unused")
	private double ucsCost;

	// linear regression for cost (given level) === gamma + delta*level
	private double ucsGamma;
	private double ucsDelta;

	public UcsModel() {
		random = new Random();

		ucsLevel = 1.0;
		ucsCost = 0.0;
		ucsBidPercentile = 0.8;

		// logistic regression initial values and constants for
		// prob(toplevel | bid).
		ucsAlpha = -10.0;
		ucsBeta = 10.0;

		// initial linear regression parameters
		ucsGamma = 0.0;
		ucsDelta = 1.0;

		/* initial bid */
		ucsBid = ucsBidByPercentile();

	}

	private double ucsFactor(double percentile) {
		// We are bidding at percentile% of winning top level
		return Math.log((1.0 / percentile) - 1.0);
	}

	private double ucsBidByPercentile() {
		return (ucsFactor(ucsBidPercentile) - ucsAlpha) / ucsBeta;
	}

	public double getBid() {
		return ucsBid;
	}

	@SuppressWarnings("unused")
	public double getCost(double level) {
		return ucsGamma + ucsDelta * level;
	}

	public void ucsUpdate(double level, double cost, boolean bidHigh) {
		double yk = level == 1.0 ? 1.0 : 0;

		// apply logistic regression update for pr(Top|bid) parameters,
		// using current ucsBidPercentile and ucsBid
		ucsAlpha += ucsLearningRate * (yk - ucsBidPercentile);
		ucsBeta += ucsLearningRate * (yk - ucsBidPercentile) * ucsBid;

		// set new bidPercentile and ucsBid,
		// bid at ucsBidPercentile probability of winning top level
		ucsBidPercentile = bidHigh ? 0.9 : 0.9 * random.nextDouble();
		ucsBid = ucsBidByPercentile();

		// apply linear regression update for (Cost|level) parameters
		ucsGamma += ucsLearningRate
				* (cost - (ucsGamma + ucsDelta * level));
		ucsDelta += ucsLearningRate
				* (cost - (ucsGamma + ucsDelta * level)) * level;
	}

}