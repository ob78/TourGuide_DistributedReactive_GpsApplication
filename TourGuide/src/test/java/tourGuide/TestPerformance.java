package tourGuide;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import gpsUtil.location.Location;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Ignore;
import org.junit.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.mockito.Mockito;
import rewardCentral.RewardCentral;
import tourGuide.helper.InternalTestHelper;
import tourGuide.service.RewardsService;
import tourGuide.service.TourGuideService;
import tourGuide.user.User;
import tourGuide.user.UserReward;

public class TestPerformance {
	
	/*
	 * A note on performance improvements:
	 *     
	 *     The number of users generated for the high volume tests can be easily adjusted via this method:
	 *     
	 *     		InternalTestHelper.setInternalUserNumber(100000);
	 *     
	 *     
	 *     These tests can be modified to suit new solutions, just as long as the performance metrics
	 *     at the end of the tests remains consistent. 
	 * 
	 *     These are performance metrics that we are trying to hit:
	 *     
	 *     highVolumeTrackLocation: 100,000 users within 15 minutes:
	 *     		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
     *
     *     highVolumeGetRewards: 100,000 users within 20 minutes:
	 *          assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */
	
	//@Ignore
	@Test
	public void highVolumeTrackLocation() {
		//Added to fix NumberFormatException due to decimal number separator
		Locale.setDefault(new Locale("en", "US"));

		// Users should be incremented up to 100,000, and test finishes within 15 minutes
		InternalTestHelper.setInternalUserNumber(100000);

		GpsUtil gpsUtil = new GpsUtil();
		RewardCentral rewardCentral = new RewardCentral();

		//RewardsService rewardsService = new RewardsService(gpsUtil, rewardCentral);
		RewardsService mockRewardsService = Mockito.spy(new RewardsService(gpsUtil,rewardCentral ));

		doNothing().when(mockRewardsService).calculateRewards(any(User.class));

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, mockRewardsService);
		tourGuideService.tracker.stopTracking();

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		//DEBUT_VERSION_AMELIOREE
		ForkJoinPool forkJoinPool = new ForkJoinPool(100);

		allUsers.forEach((user)-> {
			CompletableFuture
					.runAsync(()->tourGuideService.trackUserLocation(user), forkJoinPool)
					.thenAccept(unused->mockRewardsService.calculateRewards(user));
		});

		boolean result = forkJoinPool.awaitQuiescence(15,TimeUnit.MINUTES);
		//FIN_VERSION_AMELIOREE

		//VERSION_INITIALE
		/*
		for(User user : allUsers) {
			tourGuideService.trackUserLocation(user); // deja lance dans ToutGuideService tracker ?
		}
		*/

		stopWatch.stop();

		//tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
		assertTrue(result);
	}
	
	//@Ignore
	@Test
	public void highVolumeGetRewards() {
		//Added to fix NumberFormatException due to decimal number separator
		Locale.setDefault(new Locale("en", "US"));

		// Users should be incremented up to 100,000, and test finishes within 20 minutes
		InternalTestHelper.setInternalUserNumber(100000);

		/*
		GpsUtil mockGpsUtil =  Mockito.spy(new GpsUtil());
		RewardsService rewardsService = new RewardsService(mockGpsUtil, new RewardCentral());

		TourGuideService mockTourGuideService = Mockito.spy(new TourGuideService(mockGpsUtil,rewardsService ));
		*/

		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		VisitedLocation visitedLocationRandom = new VisitedLocation(new UUID(1,1), new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime());

		TourGuideService mockTourGuideService = Mockito.spy(new TourGuideService(gpsUtil,rewardsService));

		doReturn(visitedLocationRandom).when(mockTourGuideService).trackUserLocation(any(User.class));

		mockTourGuideService.tracker.stopTracking();

		Attraction attraction = gpsUtil.getAttractions().get(0);

		List<User> allUsers = mockTourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		//DEBUT_VERSION_AMELIOREE
		ForkJoinPool forkJoinPool = new ForkJoinPool(100);

		allUsers.forEach((user)-> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));
			CompletableFuture
					.runAsync(()->mockTourGuideService.trackUserLocation(user), forkJoinPool)
					.thenAccept(unused->rewardsService.calculateRewards(user));
		});

		boolean result = forkJoinPool.awaitQuiescence(20,TimeUnit.MINUTES);
		//FIN_VERSION_AMELIOREE

		//VERSION_INITIALE
		/*
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		
		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));
	     
	    allUsers.forEach(u -> rewardsService.calculateRewards(u));
	    */

		stopWatch.stop();

		for(User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}

		System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds."); 
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
		assertTrue(result);
	}

	//@Ignore
	@Test
	public void highVolumeTrackLocationAndGetRewards() {
		//Added to fix NumberFormatException due to decimal number separator
		Locale.setDefault(new Locale("en", "US"));

		// Users should be incremented up to 100,000, and test finishes within 15 minutes
		InternalTestHelper.setInternalUserNumber(100000);

		GpsUtil gpsUtil = new GpsUtil();
		RewardCentral rewardCentral = new RewardCentral();

		RewardsService rewardsService = new RewardsService(gpsUtil, rewardCentral);

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		tourGuideService.tracker.stopTracking();

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		ForkJoinPool forkJoinPool = new ForkJoinPool(100);

		allUsers.forEach((user)-> {
			CompletableFuture
					.runAsync(()->tourGuideService.trackUserLocation(user), forkJoinPool)
					.thenAccept(unused->rewardsService.calculateRewards(user));
		});

		boolean result = forkJoinPool.awaitQuiescence(15,TimeUnit.MINUTES);

		stopWatch.stop();

		System.out.println("highVolumeTrackLocation: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
		assertTrue(result);
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}