package tourGuide;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import gpsModule.service.GpsServiceImpl;
import gpsModule.service.IGpsService;
import gpsUtil.location.Location;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Ignore;
import org.junit.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.mockito.Mockito;
import preferencesModule.service.IPreferencesService;
import preferencesModule.service.PreferencesServiceImpl;
import rewardCentral.RewardCentral;
import rewardModule.service.IRewardsService;
import tourGuide.helper.InternalTestHelper;
import rewardModule.service.RewardsServiceImpl;
import tourGuide.service.TourGuideService;
import tourGuide.domain.User;
import tripPricer.TripPricer;
import utils.TourGuideTestUtil;

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

		// ARRANGE
		// Users should be incremented up to 100,000, and test finishes within 15 minutes
		InternalTestHelper.setInternalUserNumber(100);

		IGpsService gpsService = new GpsServiceImpl(new GpsUtil());
		//IRewardsService rewardsService = new RewardsServiceImpl(gpsService, new RewardCentral());
		IRewardsService mockRewardsService = Mockito.spy(new RewardsServiceImpl(gpsService, new RewardCentral() ));
		IPreferencesService preferencesService = new PreferencesServiceImpl(new TripPricer());

		doNothing().when(mockRewardsService).calculateRewards(any(User.class));

		TourGuideService tourGuideService = new TourGuideService(gpsService, mockRewardsService, preferencesService);
		tourGuideService.tracker.stopTracking();

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// ACT
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

		// ASSERT
		System.out.println("highVolumeTrackLocation: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
		assertTrue(result);
	}
	
	//@Ignore
	@Test
	public void highVolumeGetRewards() {
		//Added to fix NumberFormatException due to decimal number separator
		Locale.setDefault(new Locale("en", "US"));

		// ARRANGE
		// Users should be incremented up to 100,000, and test finishes within 20 minutes
		InternalTestHelper.setInternalUserNumber(100);

		/*
		GpsUtil mockGpsUtil =  Mockito.spy(new GpsUtil());
		RewardsService rewardsService = new RewardsService(mockGpsUtil, new RewardCentral());

		TourGuideService mockTourGuideService = Mockito.spy(new TourGuideService(mockGpsUtil,rewardsService ));
		*/

		IGpsService gpsService = new GpsServiceImpl(new GpsUtil());
		IRewardsService rewardsService = new RewardsServiceImpl(gpsService, new RewardCentral());
		IPreferencesService preferencesService = new PreferencesServiceImpl(new TripPricer());

		VisitedLocation visitedLocationRandom = new VisitedLocation(UUID.randomUUID(), new Location(TourGuideTestUtil.generateRandomLatitude(), TourGuideTestUtil.generateRandomLongitude()), TourGuideTestUtil.getRandomTime());

		TourGuideService mockTourGuideService = Mockito.spy(new TourGuideService(gpsService, rewardsService, preferencesService));

		doReturn(visitedLocationRandom).when(mockTourGuideService).trackUserLocation(any(User.class));

		mockTourGuideService.tracker.stopTracking();

		Attraction attraction = gpsService.getAttractions().get(0);

		List<User> allUsers = mockTourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// ACT
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

		// ASSERT
		for(User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}

		System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds."); 
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
		assertTrue(result);
	}

	// New test added : highVolumeTrackLocationAndGetRewards in actual conditions
	//@Ignore
	@Test
	public void highVolumeTrackLocationAndGetRewards() {
		//Added to fix NumberFormatException due to decimal number separator
		Locale.setDefault(new Locale("en", "US"));

		// ARRANGE
		// Users should be incremented up to 100,000, and test finishes within 15 minutes
		InternalTestHelper.setInternalUserNumber(100);
		IGpsService gpsService = new GpsServiceImpl(new GpsUtil());
		IRewardsService rewardsService = new RewardsServiceImpl(gpsService, new RewardCentral());
		IPreferencesService preferencesService = new PreferencesServiceImpl(new TripPricer());
		TourGuideService tourGuideService = new TourGuideService(gpsService, rewardsService, preferencesService);
		tourGuideService.tracker.stopTracking();

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		ForkJoinPool forkJoinPool = new ForkJoinPool(100);

		// ACT
		allUsers.forEach((user)-> {
			CompletableFuture
					.runAsync(()->tourGuideService.trackUserLocation(user), forkJoinPool)
					.thenAccept(unused->rewardsService.calculateRewards(user));
		});

		boolean result = forkJoinPool.awaitQuiescence(15,TimeUnit.MINUTES);

		stopWatch.stop();

		// ASSERT
		System.out.println("highVolumeTrackLocation: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
		assertTrue(result);
	}

}
