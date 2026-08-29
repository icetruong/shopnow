package com.ice.shippingservice.Service;

import com.ice.shippingservice.Config.CarrierProperties;
import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Exception.InvalidWebhookException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureVerifierTest {

    private static String hmac(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private WebhookSignatureVerifier verifier(String mode, String ghnSecret) {
        CarrierProperties props = new CarrierProperties();
        props.setMode(mode);
        props.getGhn().setWebhookSecret(ghnSecret);
        return new WebhookSignatureVerifier(props);
    }

    @Test
    void mockMode_onlyRequiresHeaderPresence() {
        WebhookSignatureVerifier v = verifier("mock", null);
        assertThatCode(() -> v.verify("anything", "X-GHN-Signature")).doesNotThrowAnyException();
        assertThatThrownBy(() -> v.verify(" ", "X-GHN-Signature"))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void realMode_acceptsValidHmacAndRejectsBadOne() throws Exception {
        byte[] body = "{\"orderCode\":\"GHN1\"}".getBytes(StandardCharsets.UTF_8);
        WebhookSignatureVerifier v = verifier("real", "s3cr3t");

        String good = hmac(body, "s3cr3t");
        assertThatCode(() -> v.verify(good, "X-GHN-Signature", body, CarrierType.GHN))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> v.verify("deadbeef", "X-GHN-Signature", body, CarrierType.GHN))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void realMode_failsWhenSecretNotConfigured() {
        WebhookSignatureVerifier v = verifier("real", "  ");
        assertThatThrownBy(() -> v.verify("x", "X-GHN-Signature", new byte[0], CarrierType.GHN))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessageContaining("webhook-secret");
    }
}
