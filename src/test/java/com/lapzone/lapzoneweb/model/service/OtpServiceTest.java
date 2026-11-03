package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.service.OtpService.OtpException;
import com.lapzone.lapzoneweb.model.service.OtpService.Purpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import org.mockito.ArgumentCaptor;

class OtpServiceTest {

    private static final String EMAIL = "user@example.com";
    private JavaMailSender mailSender;
    private MutableClock clock;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        otpService = new OtpService(mailSender, false, "sender@example.com", clock);
    }

    @Test
    void generatesSixDigitOtpAndConsumesItAfterOneSuccessfulUse() {
        MockHttpSession session = new MockHttpSession();
        otpService.sendOtp(session, Purpose.REGISTER, " User@Example.com ");
        String code = sentCode();

        assertTrue(code.matches("[0-9]{6}"));
        assertEquals(Optional.of(EMAIL), otpService.verifyOtp(session, Purpose.REGISTER, code));
        assertEquals(Optional.empty(), otpService.verifyOtp(session, Purpose.REGISTER, code));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertEquals(EMAIL, message.getValue().getTo()[0]);
        assertTrue(message.getValue().getText().contains("10 phút"));
        assertTrue(message.getValue().getText().contains("Nếu không phải bạn"));
    }

    @Test
    void expiredOtpIsRejected() {
        MockHttpSession session = new MockHttpSession();
        otpService.sendOtp(session, Purpose.REGISTER, EMAIL);
        String code = sentCode();
        clock.advance(OtpService.OTP_TTL);

        assertEquals(Optional.empty(), otpService.verifyOtp(session, Purpose.REGISTER, code));
        assertEquals(Optional.empty(), otpService.verifyOtp(session, Purpose.REGISTER, code));
    }

    @Test
    void fifthIncorrectEntryInvalidatesOtp() {
        MockHttpSession session = new MockHttpSession();
        otpService.sendOtp(session, Purpose.RESET_PASSWORD, EMAIL);
        String code = sentCode();
        String wrongCode = code.equals("000000") ? "000001" : "000000";

        for (int attempt = 0; attempt < OtpService.MAX_VERIFICATION_FAILURES; attempt++) {
            assertEquals(Optional.empty(), otpService.verifyOtp(session, Purpose.RESET_PASSWORD, wrongCode));
        }
        assertEquals(Optional.empty(), otpService.verifyOtp(session, Purpose.RESET_PASSWORD, code));
    }

    @Test
    void resendWithinSixtySecondsIsBlocked() {
        MockHttpSession session = new MockHttpSession();
        otpService.sendOtp(session, Purpose.REGISTER, EMAIL);

        assertThrows(OtpException.class, () -> otpService.sendOtp(session, Purpose.REGISTER, EMAIL));
        assertEquals(60, otpService.secondsUntilResend(EMAIL));
    }

    @Test
    void sixthSendWithinOneHourIsBlocked() {
        MockHttpSession session = new MockHttpSession();
        for (int send = 0; send < OtpService.MAX_SENDS_PER_HOUR; send++) {
            if (send > 0) {
                clock.advance(OtpService.RESEND_INTERVAL);
            }
            otpService.sendOtp(session, Purpose.RESET_PASSWORD, EMAIL);
        }
        clock.advance(OtpService.RESEND_INTERVAL);

        assertThrows(OtpException.class, () -> otpService.sendOtp(session, Purpose.RESET_PASSWORD, EMAIL));
        verify(mailSender, org.mockito.Mockito.times(OtpService.MAX_SENDS_PER_HOUR))
                .send(any(SimpleMailMessage.class));
    }

    @Test
    void consoleModePrintsOtpWithoutSendingEmail() {
        OtpService consoleService = new OtpService(mailSender, true, "", clock);
        consoleService.sendOtp(new MockHttpSession(), Purpose.REGISTER, EMAIL);

        verifyNoMoreInteractions(mailSender);
    }

    private String sentCode() {
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        Matcher matcher = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(message.getValue().getText());
        assertTrue(matcher.find());
        return matcher.group();
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(java.time.Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
