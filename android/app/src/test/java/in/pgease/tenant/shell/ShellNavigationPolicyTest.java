package in.pgease.tenant.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ShellNavigationPolicyTest {

    private ShellNavigationPolicy policy;

    @Before
    public void setUp() {
        policy = new ShellNavigationPolicy("https://tenant.pgease.com?app=1");
    }

    @Test
    public void keepsTenantAndEasebuzzInsideTheWebView() {
        assertEquals(
            ShellNavigationPolicy.Decision.LOAD,
            policy.decide("https://tenant.pgease.com/payments?app=1")
        );
        assertEquals(
            ShellNavigationPolicy.Decision.LOAD,
            policy.decide("https://pay.easebuzz.in/pay/abc123")
        );
        assertEquals(
            ShellNavigationPolicy.Decision.LOAD,
            policy.decide("https://testpay.easebuzz.in/v2/pay")
        );
        assertEquals(
            ShellNavigationPolicy.Decision.LOAD,
            policy.decide("https://tenant.pgease.com/payments/result?txnid=pg_1&status=success")
        );
    }

    @Test
    public void sendsUpiPhoneMailAndWhatsAppOutside() {
        assertEquals(
            ShellNavigationPolicy.Decision.EXTERNAL,
            policy.decide("upi://pay?pa=owner@okaxis&pn=PG%20Ease&am=1500.00&cu=INR&tn=Rent")
        );
        assertEquals(ShellNavigationPolicy.Decision.EXTERNAL, policy.decide("tez://upi/pay?pa=owner@okaxis&am=10.00&cu=INR"));
        assertEquals(ShellNavigationPolicy.Decision.EXTERNAL, policy.decide("phonepe://pay?pa=owner@ybl"));
        assertEquals(ShellNavigationPolicy.Decision.EXTERNAL, policy.decide("tel:+919876543210"));
        assertEquals(ShellNavigationPolicy.Decision.EXTERNAL, policy.decide("mailto:hello@pgease.com"));
        assertEquals(ShellNavigationPolicy.Decision.EXTERNAL, policy.decide("https://wa.me/919876543210"));
        assertEquals(
            ShellNavigationPolicy.Decision.EXTERNAL,
            policy.decide("https://api.whatsapp.com/send?phone=919876543210&text=Hi")
        );
        assertEquals(
            ShellNavigationPolicy.Decision.EXTERNAL,
            policy.decide("intent://pay?pa=owner@okaxis#Intent;scheme=upi;end")
        );
        assertTrue(ShellNavigationPolicy.isUpiScheme("upi://pay?pa=a@upi"));
        assertFalse(ShellNavigationPolicy.isUpiScheme("https://pay.easebuzz.in/pay/1"));
    }

    @Test
    public void downloadsPdfReceiptsAndAgreements() {
        assertEquals(
            ShellNavigationPolicy.Decision.DOWNLOAD,
            policy.decide("https://cdn.example.com/receipts/rent-may.pdf")
        );
        assertEquals(
            ShellNavigationPolicy.Decision.DOWNLOAD,
            policy.decide("https://files.pgease.com/agreements/lease.PDF?token=abc")
        );
        assertEquals(
            ShellNavigationPolicy.Decision.LOAD,
            policy.decide("https://tenant.pgease.com/documents")
        );
    }

    @Test
    public void rootIsOnlyHomeAndLogin() {
        assertTrue(policy.isInAppRoot("https://tenant.pgease.com/?app=1"));
        assertTrue(policy.isInAppRoot("https://tenant.pgease.com?app=1"));
        assertTrue(policy.isInAppRoot("https://tenant.pgease.com/login"));
        assertFalse(policy.isInAppRoot("https://tenant.pgease.com/payments"));
        assertFalse(policy.isInAppRoot("https://pay.easebuzz.in/pay/abc"));
        assertTrue(policy.isInAppRoot(null));
    }

    @Test
    public void mapsDeepLinksOntoTheTenantOrigin() {
        assertEquals(
            "https://tenant.pgease.com/payments?app=1",
            policy.toInAppUrl("https://tenant.pgease.com/payments")
        );
        assertEquals(
            "https://tenant.pgease.com/payments/result?txnid=1&app=1",
            policy.toInAppUrl("pgease://tenant/payments/result?txnid=1")
        );
        assertEquals(
            "https://tenant.pgease.com/payments?app=1",
            policy.toInAppUrl("https://tenant.pgease.com/payments?app=1")
        );
        assertNull(policy.toInAppUrl("https://example.com/payments"));
        assertNull(policy.toInAppUrl("upi://pay?pa=a@upi"));
    }

    @Test
    public void blocksFileAndCapacitorProxyUrls() {
        assertEquals(ShellNavigationPolicy.Decision.CANCEL, policy.decide("file:///sdcard/secret.pdf"));
        assertEquals(
            ShellNavigationPolicy.Decision.CANCEL,
            policy.decide("https://tenant.pgease.com/_capacitor_http_interceptor_/x")
        );
        assertEquals(ShellNavigationPolicy.Decision.LOAD, policy.decide("about:blank"));
    }
}
