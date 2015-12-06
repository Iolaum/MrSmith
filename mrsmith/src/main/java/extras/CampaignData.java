package extras;

import java.util.Map;
import java.util.Set;

import config.GameConstants;
import tau.tac.adx.demand.CampaignStats;
import tau.tac.adx.props.AdxQuery;
import tau.tac.adx.report.adn.MarketSegment;
import tau.tac.adx.report.demand.CampaignOpportunityMessage;
import tau.tac.adx.report.demand.InitialCampaignMessage;


public class CampaignData {
	/* campaign attributes as set by server */
	long reachImps;
	long dayStart;
	long dayEnd;
	Set<MarketSegment> targetSegment;
	double videoCoef;
	double mobileCoef;
	double segmentProbability;
	double reachLevel;
	int id;
	private AdxQuery[] campaignQueries;//array of queries relevant for the campaign.

	/* campaign info as reported */
	CampaignStats stats;
	double budget;

	public CampaignData(InitialCampaignMessage icm) {
		reachImps = icm.getReachImps();
		dayStart = icm.getDayStart();
		dayEnd = icm.getDayEnd();
		targetSegment = icm.getTargetSegment();
		videoCoef = icm.getVideoCoef();
		mobileCoef = icm.getMobileCoef();
		id = icm.getId();

		stats = new CampaignStats(0, 0, 0);
		budget = 0.0;
		this.setSegmentProbability();
		this.setReachLevel();
	}

	public void setBudget(double d) {
		budget = d;
	}

	public CampaignData(CampaignOpportunityMessage com) {
		dayStart = com.getDayStart();
		dayEnd = com.getDayEnd();
		id = com.getId();
		reachImps = com.getReachImps();
		targetSegment = com.getTargetSegment();
		mobileCoef = com.getMobileCoef();
		videoCoef = com.getVideoCoef();
		stats = new CampaignStats(0, 0, 0);
		budget = 0.0;
		this.setSegmentProbability();
		this.setReachLevel();
	}

	public long getDayStart() {
		return dayStart;
	}

	public long getDayEnd() {
		return dayEnd;
	}

	public long getReachImps() {
		return reachImps;
	}

	public Set<MarketSegment> getMarketSegment() {
		return targetSegment;
	}

	public double getVideoCoef() {
		return videoCoef;
	}

	public double getMobileCoef() {
		return mobileCoef;
	}

	public double getSegmentProbability() {
		return segmentProbability;
	}

	public double getReachLevel() {
		return this.reachLevel;
	}

	public int getId() {
		return id;
	}

	public double getBudget() {
		return budget;
	}

	public CampaignStats getStats() {
		return stats;
	}

	public long getLength() {
		return dayEnd - dayStart + 1;
	}

	@Override
	public String toString() {
		return "Campaign ID " + id + ": " + "day " + dayStart + " to "
				+ dayEnd + " " + targetSegment + ", reach: " + reachImps
				+ " coefs: (v=" + videoCoef + ", m=" + mobileCoef + ")";
	}

	public int impsTogo() {
		return (int) Math.max(0, reachImps - stats.getTargetedImps());
	} // GameConstants.campaignGoal*

	public int impsWeWant() {
		return (int) Math.max(0, GameConstants.campaignGoal*reachImps - stats.getTargetedImps());
	}

	public double getRemainingDays(int day) {
		return dayEnd - day;
	}

	public double getRBidGuide(int day) {
		double daysRatio = this.getRemainingDays(day)/this.getLength();
		double impsRatio = this.impsWeWant()/(GameConstants.campaignGoal*this.getReachImps());

		double factor = 1 + (impsRatio-daysRatio);

		return (0.6+this.reachLevel)*Math.pow(factor, GameConstants.rbidGuideFactor);
	}

	public void setStats(CampaignStats s) {
		stats.setValues(s);
	}

	public AdxQuery[] getCampaignQueries() {
		return campaignQueries;
	}

	public void setCampaignQueries(AdxQuery[] campaignQueries) {
		this.campaignQueries = campaignQueries;
	}

	private void setSegmentProbability() {
		Map<Set<MarketSegment>,Integer> segUsrMap = MarketSegment.usersInMarketSegments();
		this.segmentProbability = segUsrMap.get(this.targetSegment);
	}

	private void setReachLevel() {
		this.reachLevel = this.reachImps/(this.segmentProbability*this.getLength());
	}

}