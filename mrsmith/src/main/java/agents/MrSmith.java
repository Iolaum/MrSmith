package agents;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import config.GameConstants;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BankStatus;
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

	/*
	 * current day of simulation
	 */
	private int day;
	private String[] publisherNames;
	private CampaignData currCampaign;

	private Double cmpBidMillis;
	private double qualityScore;
	private UcsModel ucsModel;

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
				hadnleCampaignAuctionReport((CampaignAuctionReport) content);
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

	private void hadnleCampaignAuctionReport(CampaignAuctionReport content) {
		// ingoring
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

		Random random = new Random();
		long cmpimps = com.getReachImps();
		//long cmpBidMillis = random.nextInt((int)cmpimps);
		//# Campaign bid value --- Set here

		//cmpBidMillis = (new Double(cmpimps)) * qualityScore - 1;
		// #Calculate cmpBidMillis
		// Get campaign Length
		long cmpLength = com.getDayEnd() - com.getDayStart() +1;
		System.out.println(" cmpLength : " + cmpLength);
		//Get campaign segment and return segment probability
		Set<MarketSegment> tgtSeg = com.getTargetSegment();
		Map<Set<MarketSegment>,Integer> segUsrMap = MarketSegment.usersInMarketSegments();

		//Unknown case?
		int tgtSegmentProb = segUsrMap.get(tgtSeg);
		System.out.println(" Target Segment Probability : " + tgtSegmentProb);

		double reachLevel = 0 ;
		reachLevel = (double)cmpimps/(tgtSegmentProb*cmpLength);


		System.out.println(" Reach Level: " + reachLevel);
		System.out.println(" cmpimps: " + cmpimps);
		System.out.println(" qualityScore: " + qualityScore);

		cmpBidMillis = 0.9*reachLevel*cmpimps * qualityScore ;
		//cmpBidMillis = ((new Double(cmpimps)) * qualityScore * 0.4) - 1;
		System.out.println(" CmpBidMillis: " + cmpBidMillis);

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
					ucsBid += 0.7*campaign.budget/(campaign.dayEnd - campaign.dayStart + 1);
					//# * 0.5 to be made function with learning rate.
				}
			}

			System.out.println("Day " + day + ": ucs level reported: " + ucsLevel);
		} else {
			System.out.println("Day " + day + ": Initial ucs bid is " + ucsBid);
		}

		/* Note: Campaign bid is in millis */
		AdNetBidMessage bids = new AdNetBidMessage(ucsBid, pendingCampaign.id, cmpBidMillis.longValue());
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

		if ((pendingCampaign.id == adNetworkDailyNotification.getCampaignId())
				&& (notificationMessage.getCostMillis() != 0)) {

			/* add campaign to list of won campaigns */
			pendingCampaign.setBudget(notificationMessage.getCostMillis()/1000.0);
			//# Budget??
			currCampaign = pendingCampaign;
			genCampaignQueries(currCampaign);
			myCampaigns.put(pendingCampaign.id, pendingCampaign);

			campaignAllocatedTo = " WON at cost (Millis)"
					+ notificationMessage.getCostMillis();
			// CostMillis = Campaign Budget(?)
		}

		//		ucsModel.ucsUpdate(notificationMessage.getServiceLevel(),
		//				notificationMessage.getPrice(), activeCampaigns());


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

		/*		System.out.println("Day " + day + ": Ucs bid is "
				+ (ucsModel != null ? ucsModel.getBid() : "...No Model"));
		// Note: Campaign bid is in millis
		AdNetBidMessage bids = new AdNetBidMessage(
				ucsModel != null ? ucsModel.getBid() : 0,
				pendingCampaign != null ? pendingCampaign.id : 0,
				cmpBidMillis != null ? cmpBidMillis.longValue() : 0);
		 */
		System.out.println("Day " + day + " ended. Starting next day");
		++day;
	}


	//	private boolean activeCampaigns() {
	//		int dayBiddingFor = day + 1;
	//		for (CampaignData cmpgn : myCampaigns.values()) {
	//			if ((dayBiddingFor >= cmpgn.dayStart)
	//					&& (dayBiddingFor <= cmpgn.dayEnd)
	//					&& (cmpgn.impsTogo() > 0)) {
	//				return true;
	//			}
	//		}
	//		return false;
	//	}

	/**
	 *
	 */
	protected void sendBidAndAds() {

		bidBundle = new AdxBidBundle();

		/*
		 *
		 */

		int dayBiddingFor = day + 1;

		/* A fixed random bid, for all queries of the campaign */
		/*
		 * Note: bidding per 1000 imps (CPM) - no more than average budget
		 * revenue per imp
		 */

		double rbid = 0;

		/*
		 * add bid entries w.r.t. each active campaign with remaining contracted
		 * impressions.
		 *
		 * for now, a single entry per active campaign is added for queries of
		 * matching target segment.
		 */

		for (CampaignData campaign : myCampaigns.values()) {
			if (isCampaignActive(campaign)) {

				rbid = 0.3*campaign.budget/campaign.reachImps;
				System.out.println("rbid =  " + rbid + " || budget = " + campaign.budget);
				int entCount = 0;

				System.out.println("Campaign ID: " + campaign.id + " -- Impressions to go =  " + campaign.impsTogo());
				for (AdxQuery query : campaign.campaignQueries) {
					if (campaign.impsTogo() - entCount > 0) {
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
								entCount += campaign.videoCoef;
								rbid = campaign.videoCoef*rbid;
							}
						} else {
							if (query.getAdType() == AdType.text) {
								entCount+=campaign.mobileCoef;
								rbid = campaign.mobileCoef*rbid;
							} else {
								entCount += campaign.videoCoef + campaign.mobileCoef;
								rbid = (campaign.mobileCoef+campaign.videoCoef)*rbid;
							}

						}

						bidBundle.addQuery(query, rbid, new Ad(null),
								campaign.id, 1);
					}
				}

				double impressionLimit = campaign.impsTogo();
				double budgetLimit = campaign.budget;
				bidBundle.setCampaignDailyLimit(campaign.id,
						(int) impressionLimit, budgetLimit);

				//# Problem: understand why the query entry inside is a FEMALE

				System.out.println("Day " + day + ": Updated " + entCount
						+ " Bid Bundle entries for Campaign id " + campaign.id);
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

		ucsModel = new UcsModel();

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
					campaignData.targetSegment, Device.mobile, AdType.text));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.mobile, AdType.video));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.pc, AdType.text));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.pc, AdType.video));
		}

		campaignData.campaignQueries = new AdxQuery[campaignQueriesSet.size()];
		campaignQueriesSet.toArray(campaignData.campaignQueries);
		System.out.println("!!!!!!!!!!!!!!!!!!!!!!"+Arrays.toString(campaignData.campaignQueries)+"!!!!!!!!!!!!!!!!");


	}

	private class UcsModel {
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


	private class CampaignData {
		/* campaign attributes as set by server */
		long reachImps;
		long dayStart;
		long dayEnd;
		Set<MarketSegment> targetSegment;
		double videoCoef;
		double mobileCoef;
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
		}

		@Override
		public String toString() {
			return "Campaign ID " + id + ": " + "day " + dayStart + " to "
					+ dayEnd + " " + targetSegment + ", reach: " + reachImps
					+ " coefs: (v=" + videoCoef + ", m=" + mobileCoef + ")";
		}

		int impsTogo() {
			return (int) Math.max(0, 1.05*reachImps - stats.getTargetedImps());
		}

		void setStats(CampaignStats s) {
			stats.setValues(s);
		}

		public AdxQuery[] getCampaignQueries() {
			return campaignQueries;
		}

		public void setCampaignQueries(AdxQuery[] campaignQueries) {
			this.campaignQueries = campaignQueries;
		}

	}

	private boolean isCampaignActive(CampaignData campaign) {
		int dayBiddingFor = day + 1;
		if ((dayBiddingFor >= campaign.dayStart)
				&& (dayBiddingFor <= campaign.dayEnd)
				&& (campaign.impsTogo() > 0)) {
			return true;
		}
		return false;
	}


}
