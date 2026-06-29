package tests;

import base.BaseTest;
import base.DriverFactory;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pages.BookingPage;
import pages.LoginPage;

import java.util.concurrent.atomic.AtomicBoolean;

public class BookingTests extends BaseTest {

    @DataProvider(name = "movies")
    public Object[][] movieIds() {
        return new Object[][] { { "movie-book-3", "show-12" } };
    }

    @Test(dataProvider = "movies", groups = { "regression", "booking",
            "FRD_2_5" }, description = "FRD_2.5.7: Proceeding without a selected seat should show validation feedback")
    public void FRD_251_bookingRequiresAtLeastOneSeat(String movieId, String showId) throws InterruptedException {
        loginAsUser();
        Thread.sleep(3000);
        BookingPage bookingPage = new BookingPage(driver);
        bookingPage.openMoviesAndBook(movieId);       // navigates to /movies, clicks book, waits for /book
        Thread.sleep(3000);
        bookingPage.selectFirstShowIfPresent();
        Thread.sleep(3000);
        bookingPage.proceedToPay();
        Thread.sleep(3000);
        Assert.assertTrue(bookingPage.waitForErrorOrStillOnBooking(),
                "Booking should ask the user to select at least one seat.");
    }

    @Test(dataProvider = "movies", groups = { "payment", "destructive", "booking", "TS_103",
            "TC_107" }, description = "TC_107: Verify booking is successful for selected movie through payment redirect boundary")
    public void TC_107_bookingCanProceedToPaymentForSelectedMovie(String movieId, String showId)
            throws InterruptedException {
        // skipIfPaymentTestsDisabled();
        // skipIfDestructiveTestsDisabled();
        loginAsUser();
        Thread.sleep(3000);
        BookingPage bookingPage = new BookingPage(driver);
        bookingPage.openMoviesAndBook(movieId);       // navigates to /movies, clicks book, waits for /book
        Thread.sleep(3000);
        bookingPage.selectFirstShowIfPresent();
        Thread.sleep(3000);
        bookingPage.selectFirstAvailableSeat();
        Thread.sleep(3000);
        Assert.assertTrue(bookingPage.hasSelectedSeatSummary(),
                "Selected seat and total should appear in booking summary.");
        bookingPage.proceedToPay();
        Thread.sleep(3000);
        Assert.assertTrue(bookingPage.navigatedToPaymentOrSuccess(),
                "Proceeding should redirect to payment or payment result page.");
    }

    @Test(groups = { "concurrency", "booking",
            "TC_CONCURRENT" }, description = "TC_CONCURRENT: Two users racing to book the same seat — only one should succeed")
    public void TC_CONCURRENT_onlyOneUserShouldBookSameSeat() throws InterruptedException {
        final String movieId = "movie-book-3";

        // Latch: both threads count down once they have selected a seat.
        // Neither proceeds to payment until BOTH have selected — then they fire together.
        final java.util.concurrent.CountDownLatch seatSelectedLatch =
                new java.util.concurrent.CountDownLatch(2);

        // ── Driver 1 – rahulkumar ─────────────────────────────────────────────────
        DriverFactory.createDriver(null, false);
        WebDriver driver1 = DriverFactory.getDriver();

        // ── Driver 2 – sanjaykumar ────────────────────────────────────────────────
        DriverFactory.createDriver(null, false);
        WebDriver driver2 = DriverFactory.getDriver();

        AtomicBoolean user1ReachedPayment = new AtomicBoolean(false);
        AtomicBoolean user2ReachedPayment = new AtomicBoolean(false);

        // Track setup failures separately so we can fail loudly if setup itself breaks
        AtomicBoolean user1SetupFailed = new AtomicBoolean(false);
        AtomicBoolean user2SetupFailed = new AtomicBoolean(false);

        // ── Thread 1: rahulkumar ──────────────────────────────────────────────────
        Thread t1 = new Thread(() -> {
            try {
                LoginPage lp1 = new LoginPage(driver1).open();
                lp1.login("rahulkumar", "123456");
                lp1.waitUntilLoggedIn();
                Thread.sleep(3000);

                BookingPage bp1 = new BookingPage(driver1);
                bp1.openMoviesAndBook(movieId);       // navigates to /movies → clicks book → waits for /book URL
                Thread.sleep(3000);

                // Dynamically pick the first available show — no hardcoded ID
                boolean showSelected = bp1.selectFirstShowIfPresent();
                Thread.sleep(3000);
                if (!showSelected) {
                    System.out.println("[rahulkumar] No show found on booking page!");
                    user1SetupFailed.set(true);
                    seatSelectedLatch.countDown(); // release latch so t2 doesn't hang
                    return;
                }

                boolean seatSelected = bp1.selectFirstAvailableSeat();
                Thread.sleep(3000);
                if (!seatSelected) {
                    System.out.println("[rahulkumar] No available seat found!");
                    user1SetupFailed.set(true);
                    seatSelectedLatch.countDown();
                    return;
                }

                // Seat selected — signal ready and wait for sanjaykumar to be ready too
                System.out.println("[rahulkumar] Seat selected. Waiting for sanjaykumar...");
                seatSelectedLatch.countDown();
                seatSelectedLatch.await(); // blocks until both are ready

                // Both are ready — wait 5 seconds so both browsers visibly hold their seat
                System.out.println("[rahulkumar] Both ready. Holding for 5 seconds before payment...");
                Thread.sleep(5000);

                System.out.println("[rahulkumar] Proceeding to pay NOW!");
                bp1.proceedToPay();
                Thread.sleep(3000);

                user1ReachedPayment.set(bp1.navigatedToPaymentOrSuccess());
                System.out.println("[rahulkumar] payment reached: " + user1ReachedPayment.get());
            } catch (Exception e) {
                System.out.println("[rahulkumar] Exception during payment flow: " + e.getMessage());
                user1ReachedPayment.set(false);
                seatSelectedLatch.countDown(); // ensure t2 is never left hanging
            }
        });

        // ── Thread 2: sanjaykumar ─────────────────────────────────────────────────
        Thread t2 = new Thread(() -> {
            try {
                LoginPage lp2 = new LoginPage(driver2).open();
                lp2.login("sanjaykumar", "123456");
                lp2.waitUntilLoggedIn();
                Thread.sleep(3000);

                BookingPage bp2 = new BookingPage(driver2);
                bp2.openMoviesAndBook(movieId);       // navigates to /movies → clicks book → waits for /book URL
                Thread.sleep(3000);

                // Dynamically pick the first available show — no hardcoded ID
                boolean showSelected = bp2.selectFirstShowIfPresent();
                Thread.sleep(3000);
                if (!showSelected) {
                    System.out.println("[sanjaykumar] No show found on booking page!");
                    user2SetupFailed.set(true);
                    seatSelectedLatch.countDown();
                    return;
                }

                boolean seatSelected = bp2.selectFirstAvailableSeat();
                Thread.sleep(3000);
                if (!seatSelected) {
                    System.out.println("[sanjaykumar] No available seat found!");
                    user2SetupFailed.set(true);
                    seatSelectedLatch.countDown();
                    return;
                }

                // Seat selected — signal ready and wait for rahulkumar to be ready too
                System.out.println("[sanjaykumar] Seat selected. Waiting for rahulkumar...");
                seatSelectedLatch.countDown();
                seatSelectedLatch.await(); // blocks until both are ready

                // Both are ready — wait 5 seconds so both browsers visibly hold their seat
                System.out.println("[sanjaykumar] Both ready. Holding for 5 seconds before payment...");
                Thread.sleep(5000);

                System.out.println("[sanjaykumar] Proceeding to pay NOW!");
                bp2.proceedToPay();
                Thread.sleep(3000);

                user2ReachedPayment.set(bp2.navigatedToPaymentOrSuccess());
                System.out.println("[sanjaykumar] payment reached: " + user2ReachedPayment.get());
            } catch (Exception e) {
                System.out.println("[sanjaykumar] Exception during payment flow: " + e.getMessage());
                user2ReachedPayment.set(false);
                seatSelectedLatch.countDown(); // ensure t1 is never left hanging
            }
        });

        // ── Launch both simultaneously ─────────────────────────────────────────────
        t1.start();
        t2.start();

        // ── Wait for both to finish (max 5 minutes) ───────────────────────────────
        t1.join(300_000);
        t2.join(300_000);

        // ── Tear down both drivers ────────────────────────────────────────────────
        try { driver1.quit(); } catch (Exception ignored) {}
        try { driver2.quit(); } catch (Exception ignored) {}

        // ── Fail loudly if setup itself broke (show/seat not found) ───────────────
        if (user1SetupFailed.get() || user2SetupFailed.get()) {
            Assert.fail("Setup failed for one or both users — could not find a show or seat. " +
                    "Check that the movie has an available show with open seats.");
        }

        // ── Assertion: if BOTH reached payment, the system has no seat-lock guard ─
        boolean bothSucceeded = user1ReachedPayment.get() && user2ReachedPayment.get();
        Assert.assertFalse(bothSucceeded,
                "FAIL: Both rahulkumar and sanjaykumar reached payment for the SAME seat simultaneously. " +
                "The system must prevent double-booking — only one user should succeed.");
    }

    // @Test(dataProvider = "movies")
    // public void checkLeftSeats(String movieId, String showId) throws
    // InterruptedException {
    // loginAsUser();
    // Thread.sleep(3000);
    // BookingPage bookingPage = new BookingPage(driver);
    // bookingPage.selectMovie(movieId);
    // Thread.sleep(3000);
    // int leftSeats =
    // Integer.parseInt(bookingPage.getSeatsLeft(showId).trim().split(" ")[0]);
    // bookingPage.selectShow(showId);
    // int enabledSeats = bookingPage.findEnabledSeats();
    //
    // Assert.assertEquals(enabledSeats, leftSeats,
    // "The number of enabled seats should match the showtime's available seat
    // count.");
    // }
}
