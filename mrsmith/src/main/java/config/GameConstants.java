package config;

public class GameConstants {
	public static final Double pUserContinuation = 0.3;
	public static final Double pRandomCampaignAllocation = 0.36;
	public static final Integer maxUserDailyImpressions = 6;
	public static final Double initialReservePrice = 0.005;
	public static final Double reservePriceVariance = 0.02;
	public static final Double reservePriceLearningRate = 0.2;
	public static final Integer shortCampaignDuration = 3;
	public static final Integer mediumCampaignDuration = 5;
	public static final Integer longCampaignDuration = 10;
	public static final Double lowCampaignReachFactor = 0.2;
	public static final Double mediumCampaignReachFactor = 0.5;
	public static final Double highCampaignReachFactor = 0.8;
	public static final Double maxCampaignCostByImpression = 0.001;
	public static final Double minCampaignCostByImpression = 0.0001;
	public static final Double qualityRatingLearningRate = 0.6;
	public static final Integer gameLength = 60;
	public static final Integer realTimeSecondsPerDay = 10;
	public static final Double pUcsUserRevelation = 0.9;
	public static final Double initialDayClassificationAcc = 0.9;
	public static final Double campaignGoal = 1.075;
	public static final Double rbidGuideFactor = 0.5;
	public static final Integer AdXGuideFactor = 4;
	public static final Double AdXRatio = 0.6;
	public static final Double CampaignCut = 0.1;
	public static final Double UCSRatio = 0.3;

}