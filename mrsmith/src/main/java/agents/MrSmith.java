package agents;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import config.GameConstants;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BankStatus;
import extras.CampaignData;
import se.sics.isl.transport.Transportable;
import se.sics.tasim.aw.Agent;
import se.sics.tasim.aw.Message;
import se.sics.tasim.props.SimulationStatus;
import se.sics.tasim.props.StartInfo;
import tau.tac.adx.ads.properties.AdType;
import tau.tac.adx.demand.CampaignStats;
import tau.tac.adx.devices.Device;
import tau.tac.adx.props.AdxBidBundle;
import tau.tac.adx.props.AdxQuery;
import tau.tac.adx.props.PublisherCatalog;
import tau.tac.adx.props.PublisherCatalogEntry;
import tau.tac.adx.report.adn.AdNetworkReport;
import tau.tac.adx.report.adn.MarketSegment;
import tau.tac.adx.report.demand.AdNetBidMessage;
import tau.tac.adx.report.demand.AdNetworkDailyNotification;
import tau.tac.adx.report.demand.CampaignOpportunityMessage;
import tau.tac.adx.report.demand.CampaignReport;
import tau.tac.adx.report.demand.CampaignReportKey;
import tau.tac.adx.report.demand.InitialCampaignMessage;
import tau.tac.adx.report.demand.campaign.auction.CampaignAuctionReport;
import tau.tac.adx.report.publisher.AdxPublisherReport;
import tau.tac.adx.report.publisher.AdxPublisherReportEntry;

/**
 *
 * @author Mariano Schain
 * Test plug-in
 *
 */
public class MrSmith extends Agent {

	private final Logger log = Logger
			.getLogger(MrSmith.class.getName());

	/*
	 * Basic simulation information. An agent should receive the {@link
	 * StartInfo} at the beginning of the game or during recovery.
	 */
	@SuppressWarnings("unused")
	private StartInfo startInfo;

	/**
	 * Messages received:
	 *
	 * We keep all the {@link CampaignReport campaign reports} delivered to the
	 * agent. We also keep the initialisation messages {@link PublisherCatalog}
	 * and {@link InitialCampaignMessage} and the most recent messages and
	 * reports {@link CampaignOpportunityMessage}, {@link CampaignReport}, and
	 * {@link AdNetworkDailyNotification}.
	 */
	private final Queue<CampaignReport> campaignReports;
	private PublisherCatalog publisherCatalog;
	private InitialCampaignMessage initialCampaignMessage;
	private AdNetworkDailyNotification adNetworkDailyNotification;

	/*
	 * The addresses of server entities to which the agent should send the daily
	 * bids data
	 */
	private String demandAgentAddress;
	private String adxAgentAddress;

	/*
	 * we maintain a list of queries - each characterised by the web site (the
	 * publisher), the device type, the ad type, and the user market segment
	 */
	private AdxQuery[] queries;

	/**
	 * Information regarding the latest campaign opportunity announced
	 */
	private CampaignData pendingCampaign;

	/**
	 * We maintain a collection (mapped by the campaign id) of the campaigns won
	 * by our agent.
	 */
	private Map<Integer, CampaignData> myCampaigns;

	/*
	 * the bidBundle to be sent daily to the AdX
	 */
	private AdxBidBundle bidBundle;

	/*
	 * The current bid level for the user classification service
	 */
	double ucsBid;

	/*
	 * The targeted service level for the user classification service
	 */
	double ucsTargetLevel;

	//# Learning bid coefficient
	double fbid = 0.25; // 0.9 when reachLevel is used.
	//# Last bid won an auction
	double lastWinBid;
	//# Last won budget
	double lastBudget;

	//# fbid limits
	double Rmin = 1000* GameConstants.minCampaignCostByImpression;
	double Rmax = 1000* GameConstants.maxCampaignCostByImpression;
	double fbidmin = 0;
	double fbidmax = 0;
	double fbidlf = 0.3;

	/*
	 * current day of simulation
	 */
	private int day;
	private String[] publisherNames;
	private CampaignData currCampaign;

	private Double cmpBidMillis;
	private double qualityScore;

	public MrSmith() {
		campaignReports = new LinkedList<CampaignReport>();
	}

	@Override
	protected void messageReceived(Message message) {
		log.setUseParentHandlers(false);
		try {
			Transportable content = message.getContent();

			if (content instanceof InitialCampaignMessage) {
				handleInitialCampaignMessage((InitialCampaignMessage) content);
			} else if (content instanceof CampaignOpportunityMessage) {
				handleICampaignOpportunityMessage((CampaignOpportunityMessage) content);
			} else if (content instanceof CampaignReport) {
				//	For each campaign we have, we get a report
				handleCampaignReport((CampaignReport) content);
			} else if (content instanceof AdNetworkDailyNotification) {
				handleAdNetworkDailyNotification((AdNetworkDailyNotification) content);
			} else if (content instanceof AdxPublisherReport) {
				handleAdxPublisherReport((AdxPublisherReport) content);
			} else if (content instanceof SimulationStatus) {
				handleSimulationStatus((SimulationStatus) content);
			} else if (content instanceof PublisherCatalog) {
				handlePublisherCatalog((PublisherCatalog) content);
			} else if (content instanceof AdNetworkReport) {
				handleAdNetworkReport((AdNetworkReport) content);
			} else if (content instanceof StartInfo) {
				handleStartInfo((StartInfo) content);
			} else if (content instanceof BankStatus) {
				handleBankStatus((BankStatus) content);
			} else if(content instanceof CampaignAuctionReport) {
				handleCampaignAuctionReport((CampaignAuctionReport) content);
			}
			else {
				System.out.println("UNKNOWN Message Received: " + content);
			}

		} catch (NullPointerException e) {
			this.log.log(Level.SEVERE,
					"Exception thrown while trying to parse message." + e);
			return;
		}
	}

	private void handleCampaignAuctionReport(CampaignAuctionReport content) {
		System.out.println(
				" ++ Day" + day +
				" Campaign Auction Report: "+
				" Campaign ID = "+ content.getCampaignID() +
				" Winner = "+ content.getWinner() +
				" Winner = "+ content.isRandomAllocation()
				);
	}

	private void handleBankStatus(BankStatus content) {
		System.out.println("Day " + day + " :" + content.toString());
	}

	/**
	 * Processes the start information.
	 *
	 * @param startInfo
	 *            the start information.
	 */
	protected void handleStartInfo(StartInfo startInfo) {
		this.startInfo = startInfo;
	}

	/**
	 * Process the reported set of publishers
	 *
	 * @param publisherCatalog
	 */
	private void handlePublisherCatalog(PublisherCatalog publisherCatalog) {
		this.publisherCatalog = publisherCatalog;
		generateAdxQuerySpace();
		getPublishersNames();
	}

	/**
	 * On day 0, a campaign (the "initial campaign") is allocated to each
	 * competing agent. The campaign starts on day 1. The address of the
	 * server's AdxAgent (to which bid bundles are sent) and DemandAgent (to
	 * which bids regarding campaign opportunities may be sent in subsequent
	 * days) are also reported in the initial campaign message
	 */
	private void handleInitialCampaignMessage(
			InitialCampaignMessage campaignMessage) {
		System.out.println("Simulation is starting! Days of competition: " + GameConstants.gameLength);
		System.out.println(campaignMessage.toString());

		day = 0;

		initialCampaignMessage = campaignMessage;
		demandAgentAddress = campaignMessage.getDemandAgentAddress();
		adxAgentAddress = campaignMessage.getAdxAgentAddress();

		CampaignData campaignData = new CampaignData(initialCampaignMessage);
		campaignData.setBudget(initialCampaignMessage.getBudgetMillis()/1000.0);
		currCampaign = campaignData;
		genCampaignQueries(currCampaign);

		/*
		 * The initial campaign is already allocated to our agent so we add it
		 * to our allocated-campaigns list.
		 */

		System.out.println("Day " + day + ": Allocated campaign - " + campaignData);

		myCampaigns.put(initialCampaignMessage.getId(), campaignData);
	}

	/**
	 * On day n ( > 0) a campaign opportunity is announced to the competing
	 * agents. The campaign starts on day n + 2 or later and the agents may send
	 * (on day n) related bids (attempting to win the campaign). The allocation
	 * (the winner) is announced to the competing agents during day n + 1.
	 */
	private void handleICampaignOpportunityMessage(
			CampaignOpportunityMessage com) {

		day = com.getDay();

		pendingCampaign = new CampaignData(com);

		System.out.println("Day " + day + ": Campaign opportunity - " + pendingCampaign);

		/*
		 * The campaign requires com.getReachImps() impressions. The competing
		 * Ad Networks bid for the total campaign Budget (that is, the ad
		 * network that offers the lowest budget gets the campaign allocated).
		 * The advertiser is willing to pay the AdNetwork at most 1$ CPM,
		 * therefore the total number of impressions may be treated as a reserve
		 * (upper bound) price for the auction.
		 */

		long cmpimps = com.getReachImps();
		// #Calculate cmpBidMillis START

		// Get campaign Length
		long cmpLength = com.getDayEnd() - com.getDayStart() +1;

		//Get campaign segment and return segment number
		Set<MarketSegment> tgtSeg = com.getTargetSegment();
		Map<Set<MarketSegment>,Integer> segUsrMap = MarketSegment.usersInMarketSegments();
		//Unknown case?
		int tgtSegmentProb = segUsrMap.get(tgtSeg);
		//Calculate reach level
		double reachLevel = 0 ;
		reachLevel = (double)cmpimps/(tgtSegmentProb*cmpLength);

		//# cmpBidMillis = fbid*reachLevel*cmpimps * qualityScore;
		//# checking another strategy that will work better against random
		//# but not against smarter oponents

		fbid = fbidmin +fbidlf*(fbidmax-fbidmin);
		//# trying "simpler" strategy.
		cmpBidMillis = fbid*cmpimps *qualityScore;
		lastWinBid = cmpBidMillis/1000.0;
		System.out.println("++ Day: " + day + " Campaign BID for day: " + day + " cmpLength : " + cmpLength +
				" Reach Level: " + reachLevel + " cmpimps: " + cmpimps +
				" Target Segment Probability : " + tgtSegmentProb);
		System.out.println("++ Day: " + day + " fbid: " + fbid + " CmpBidMillis: " + cmpBidMillis + " lastWinBid: " + lastWinBid +
				" qualityScore: " + qualityScore);

		// #cmpBidMillis END of calculation

		System.out.println("Day " + day + ": Campaign total budget bid (millis): " + cmpBidMillis.longValue());

		/*
		 * Adjust ucs bid s.t. target level is achieved. Note: The bid for the
		 * user classification service is piggybacked
		 */

		if (adNetworkDailyNotification != null) {
			double ucsLevel = adNetworkDailyNotification.getServiceLevel();
			//			ucsBid = 0.1 + random.nextDouble()/10.0;
			//# UCS Bid Value --- Set here
			ucsBid = 0;

			for (CampaignData campaign : myCampaigns.values()) {
				if (isCampaignActive(campaign)) {
					ucsBid += 0.6*campaign.getBudget()/campaign.getLength();
					//# Decreased percentage to 0.6 so we keep 10% for profits.
				}
			}

			System.out.println("Day " + day + ": ucs level reported: " + ucsLevel);
		} else {
			System.out.println("Day " + day + ": Initial ucs bid is " + ucsBid);
		}

		System.out.println("++ Day " + day + ": ucs bid is " + ucsBid);
		/* Note: Campaign bid is in millis */
		AdNetBidMessage bids = new AdNetBidMessage(ucsBid, pendingCampaign.getId(), cmpBidMillis.longValue());
		sendMessage(demandAgentAddress, bids);
	}

	/**
	 * On day n ( > 0), the result of the UserClassificationService and Campaign
	 * auctions (for which the competing agents sent bids during day n -1) are
	 * reported. The reported Campaign starts in day n+1 or later and the user
	 * classification service level is applicable starting from day n+1.
	 */
	private void handleAdNetworkDailyNotification(
			AdNetworkDailyNotification notificationMessage) {

		adNetworkDailyNotification = notificationMessage;

		qualityScore = notificationMessage.getQualityScore();

		System.out.println("Day " + day + ": Daily notification for campaign "
				+ adNetworkDailyNotification.getCampaignId());

		String campaignAllocatedTo = " allocated to "
				+ notificationMessage.getWinner();

		// # fbid limits calculation
		fbidmin = Rmin/Math.pow(qualityScore,2);
		fbidmax = 10*Rmin;
		if (fbidmin>fbidmax){
			System.out.println("Min & max CONGESTION");
		}

		if ((pendingCampaign.getId() == adNetworkDailyNotification.getCampaignId())
				&& (notificationMessage.getCostMillis() != 0)) {

			/* add campaign to list of won campaigns */
			pendingCampaign.setBudget(notificationMessage.getCostMillis()/1000.0);

			currCampaign = pendingCampaign;
			genCampaignQueries(currCampaign);
			myCampaigns.put(pendingCampaign.getId(), pendingCampaign);

			campaignAllocatedTo = " WON at cost (Millis)"
					+ notificationMessage.getCostMillis();
			// CostMillis = Campaign Budget(?)
			System.out.println("\n \n WON campaign: " + day + "\n");

			lastBudget = notificationMessage.getCostMillis()/1000.0;

			//# Testing "Simple" Strategy

			if (fbidlf*(1+(lastBudget-lastWinBid)/(4*lastWinBid)) < 0.99) {
				fbidlf = fbidlf*(1+(lastBudget-lastWinBid)/(4*lastWinBid));
			}

		} else if (0.95*fbidlf>0.01) {
			fbidlf = 0.95*fbidlf;
		}

		System.out.println("Day " + day + ": " + campaignAllocatedTo
				+ ". UCS Level set to " + notificationMessage.getServiceLevel()
				+ " at price " + notificationMessage.getPrice()
				// Price: UCS price of next lower bidder
				+ " Quality Score is: " + notificationMessage.getQualityScore());

	}

	/**
	 * The SimulationStatus message received on day n indicates that the
	 * calculation time is up and the agent is requested to send its bid bundle
	 * to the AdX.
	 */
	private void handleSimulationStatus(SimulationStatus simulationStatus) {
		System.out.println("Day " + day + " : Simulation Status Received");
		sendBidAndAds();
		System.out.println("Day " + day + " ended. Starting next day");
		++day;
	}


	protected void sendBidAndAds() {

		bidBundle = new AdxBidBundle();

		/*
		 * Note: bidding per 1000 imps (CPM) - no more than average budget
		 * revenue per imp
		 */

		double rbid = 0;
		double weightNumer = 0;
		double weightDenom = 0;
		double adjustedWeightNumer = 0;
		double adjustedWeightDenom = 0;
		double weight = 0;
		int adjustedWeight = 0;

		/*
		 * add bid entries w.r.t. each active campaign with remaining contracted
		 * impressions.
		 *
		 * for now, a single entry per active campaign is added for queries of
		 * matching target segment.
		 */

		for (CampaignData campaign : myCampaigns.values()) {
			if (isCampaignActive(campaign)) {
				weightDenom += (campaign.impsTogo2()/(GameConstants.campaignGoal*campaign.getReachImps()));
				adjustedWeightDenom += 1 / (1 + campaign.getRemainingDays(day));
			}
		}

		for (CampaignData campaign : myCampaigns.values()) {
			if (isCampaignActive(campaign)) {

				rbid = 0.3*campaign.getBudget()/campaign.getReachImps();
				System.out.println("++ Day: " + day + " rbid =  " + rbid + " || budget = " + campaign.getBudget());

				weightNumer = (campaign.impsTogo2()/(GameConstants.campaignGoal*campaign.getReachImps()));
				weight = weightNumer/weightDenom;

				adjustedWeightNumer = 1 / (1 + campaign.getRemainingDays(day));
				adjustedWeight = (int) Math.ceil(100*adjustedWeightNumer/(weight*adjustedWeightDenom));

				int entCount = 0;

				System.out.println("++ Day: " + day +
						" Campaign ID: " + campaign.getId() +
						" Impressions to go =  " + campaign.impsTogo2() +
						" Impressions to go ratio: " + weightNumer +
						" Adjusted Weight: " + adjustedWeight +
						" Days remaining: " + (int)campaign.getRemainingDays(day));

				for (AdxQuery query : campaign.getCampaignQueries()) {
					if (campaign.impsTogo2() - entCount > 0) {
						/*
						 * among matching entries with the same campaign id, the AdX
						 * randomly chooses an entry according to the designated
						 * weight. by setting a constant weight 1, we create a
						 * uniform probability over active campaigns(irrelevant because we are bidding only on one campaign)
						 */
						if (query.getDevice() == Device.pc) {
							if (query.getAdType() == AdType.text) {
								entCount++;
							} else {
								entCount ++;
								rbid = campaign.getVideoCoef()*rbid;
							}
						} else {
							if (query.getAdType() == AdType.text) {
								entCount++;
								rbid = campaign.getMobileCoef()*rbid;
							} else {
								entCount ++;
								rbid = (campaign.getMobileCoef()+campaign.getVideoCoef())*rbid;
							}

						}

						bidBundle.addQuery(query, rbid, new Ad(null),
								campaign.getId(), adjustedWeight);
					}
				}

				int impressionLimit = campaign.impsTogo2();

				if (campaign.getStats().getCost() > 0.5D * campaign.getBudget()) {
					impressionLimit = (int)(campaign.impsTogo2()/GameConstants.campaignGoal);
				}

				double budgetLimit = (1.05*0.3*campaign.getBudget()*campaign.impsTogo2())/campaign.getReachImps();
				//# added budget limit
				//# 1.05 is to be sure that we don't run out of money by a small change

				System.out.println("++ Day: " + day +
						" Campaign id " + campaign.getId() +
						" budgetLimit: " + budgetLimit);

				bidBundle.setCampaignDailyLimit(campaign.getId(),
						impressionLimit, budgetLimit);

				System.out.println("Day " + day + ": Updated " + entCount
						+ " Bid Bundle entries for Campaign id " + campaign.getId());
			}
		}

		if (bidBundle != null) {
			System.out.println("Day " + day + ": Sending BidBundle");
			sendMessage(adxAgentAddress, bidBundle);
		}
	}

	/**
	 * Campaigns performance with respect to(w.r.t.) each allocated campaign
	 */
	private void handleCampaignReport(CampaignReport campaignReport) {

		campaignReports.add(campaignReport);

		/*
		 * for each campaign, the accumulated statistics from day 1 up to day
		 * n-1 are reported
		 */
		for (CampaignReportKey campaignKey : campaignReport.keys()) {
			int cmpId = campaignKey.getCampaignId();
			CampaignStats cstats = campaignReport.getCampaignReportEntry(
					campaignKey).getCampaignStats();
			myCampaigns.get(cmpId).setStats(cstats);

			System.out.println("Day " + day + ": Updating campaign " + cmpId + " stats: "
					+ cstats.getTargetedImps() + " tgtImps "
					+ cstats.getOtherImps() + " nonTgtImps. Cost of imps is "
					+ cstats.getCost());
		}
	}

	/**
	 * Users and Publishers statistics: popularity and ad type orientation
	 */
	private void handleAdxPublisherReport(AdxPublisherReport adxPublisherReport) {
		System.out.println("Publishers Report: ");
		for (PublisherCatalogEntry publisherKey : adxPublisherReport.keys()) {
			AdxPublisherReportEntry entry = adxPublisherReport
					.getEntry(publisherKey);
			System.out.println(entry.toString());
		}
	}

	/**
	 *
	 * @param adnetReport
	 */
	private void handleAdNetworkReport(AdNetworkReport adnetReport) {

		System.out.println("Day " + day + " : AdNetworkReport");
		/*
		 * for (AdNetworkKey adnetKey : adnetReport.keys()) {
		 *
		 * double rnd = Math.random(); if (rnd > 0.95) { AdNetworkReportEntry
		 * entry = adnetReport .getAdNetworkReportEntry(adnetKey);
		 * System.out.println(adnetKey + " " + entry); } }
		 */
	}

	@Override
	protected void simulationSetup() {

		day = 0;
		bidBundle = new AdxBidBundle();

		qualityScore = 1.0;

		/* initial bid between 0.1 and 0.2 */
		ucsBid = 0.2;

		myCampaigns = new HashMap<Integer, CampaignData>();
	}

	@Override
	protected void simulationFinished() {
		campaignReports.clear();
		bidBundle = null;
	}

	/**
	 * A user visit to a publisher's web-site results in an impression
	 * opportunity (a query) that is characterised by the the publisher, the
	 * market segment the user may belongs to, the device used (mobile or
	 * desktop) and the ad type (text or video).
	 *
	 * An array of all possible queries is generated here, based on the
	 * publisher names reported at game initialisation in the publishers catalogue
	 * message
	 */
	private void generateAdxQuerySpace() {
		if (publisherCatalog != null && queries == null) {
			Set<AdxQuery> querySet = new HashSet<AdxQuery>();

			/*
			 * for each web site (publisher) we generate all possible variations
			 * of device type, ad type, and user market segment
			 */
			for (PublisherCatalogEntry publisherCatalogEntry : publisherCatalog) {
				String publishersName = publisherCatalogEntry
						.getPublisherName();
				for (MarketSegment userSegment : MarketSegment.values()) {
					Set<MarketSegment> singleMarketSegment = new HashSet<MarketSegment>();
					singleMarketSegment.add(userSegment);

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.mobile, AdType.text));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.pc, AdType.text));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.mobile, AdType.video));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.pc, AdType.video));

				}

				/**
				 * An empty segments set is used to indicate the "UNKNOWN"
				 * segment such queries are matched when the UCS fails to
				 * recover the user's segments.
				 */
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.mobile,
						AdType.video));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.mobile,
						AdType.text));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.pc, AdType.video));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.pc, AdType.text));
			}
			queries = new AdxQuery[querySet.size()];
			querySet.toArray(queries);
		}
	}

	/*generates an array of the publishers names
	 * */
	private void getPublishersNames() {
		if (null == publisherNames && publisherCatalog != null) {
			ArrayList<String> names = new ArrayList<String>();
			for (PublisherCatalogEntry pce : publisherCatalog) {
				names.add(pce.getPublisherName());
			}

			publisherNames = new String[names.size()];
			names.toArray(publisherNames);
		}
	}
	/*
	 * genarates the campaign queries relevant for the specific campaign, and assign them as the campaigns campaignQueries field
	 */
	private void genCampaignQueries(CampaignData campaignData) {
		Set<AdxQuery> campaignQueriesSet = new HashSet<AdxQuery>();
		for (String PublisherName : publisherNames) {
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.getMarketSegment(), Device.mobile, AdType.text));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.getMarketSegment(), Device.mobile, AdType.video));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.getMarketSegment(), Device.pc, AdType.text));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.getMarketSegment(), Device.pc, AdType.video));
		}

		campaignData.setCampaignQueries(new AdxQuery[campaignQueriesSet.size()]);
		campaignQueriesSet.toArray(campaignData.getCampaignQueries());
		System.out.println("!!!!!!!!!!!!!!!!!!!!!!"+Arrays.toString(campaignData.getCampaignQueries())+"!!!!!!!!!!!!!!!!");


	}

	private boolean isCampaignActive(CampaignData campaign) {
		int dayBiddingFor = day + 1;
		if ((dayBiddingFor >= campaign.getDayStart())
				&& (dayBiddingFor <= campaign.getDayEnd())
				&& (campaign.impsTogo2() > 0)) {
			return true;
		}
		return false;
	}


}
