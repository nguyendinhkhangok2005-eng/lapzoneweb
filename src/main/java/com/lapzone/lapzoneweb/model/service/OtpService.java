package com.lapzone.lapzoneweb.model.service;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class OtpService {

    public enum Purpose {
        REGISTER,
        RESET_PASSWORD
    }

    public static final Duration OTP_TTL = Duration.ofMinutes(10);
    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    public static final int MAX_SENDS_PER_HOUR = 5;
    public static final int MAX_VERIFICATION_FAILURES = 5;

    private static final Logger LOGGER = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String OTP_SESSION_PREFIX = "emailOtp.";

    private final JavaMailSender mailSender;
    private final boolean logToConsole;
    private final String mailFrom;
    private final Clock clock;
    private final ConcurrentMap<String, SendWindow> sendWindows = new ConcurrentHashMap<>();

    @Autowired
    public OtpService(JavaMailSender mailSender,
                      @Value("${app.otp.log-to-console:false}") boolean logToConsole,
                      @Value("${spring.mail.username:}") String mailFrom) {
        this(mailSender, logToConsole, mailFrom, Clock.systemUTC());
    }

    OtpService(JavaMailSender mailSender, boolean logToConsole, String mailFrom, Clock clock) {
        this.mailSender = mailSender;
        this.logToConsole = logToConsole;
        this.mailFrom = mailFrom;
        this.clock = clock;
    }

    public void sendOtp(HttpSession session, Purpose purpose, String email) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = clock.instant();
        SendWindow window = sendWindows.computeIfAbsent(normalizedEmail, ignored -> new SendWindow());

        synchronized (window) {
            window.prune(now);
            if (window.lastSentAt != null
                    && Duration.between(window.lastSentAt, now).compareTo(RESEND_INTERVAL) < 0) {
                throw new OtpException("Vui lòng đợi 60 giây trước khi gửi mã mới.");
            }
            if (window.sentAt.size() >= MAX_SENDS_PER_HOUR) {
                throw new OtpException("Bạn đã yêu cầu quá nhiều mã. Vui lòng thử lại sau.");
            }

            String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
            if (logToConsole) {
                System.out.println("OTP " + purpose + " for " + normalizedEmail + ": " + code);
            } else {
                sendEmail(purpose, normalizedEmail, code);
            }

            byte[] salt = randomSalt();
            session.setAttribute(sessionKey(purpose),
                    new OtpChallenge(normalizedEmail, salt, hashOtp(purpose, normalizedEmail, salt, code), now));
            window.lastSentAt = now;
            window.sentAt.addLast(now);
        }
    }

    public Optional<String> verifyOtp(HttpSession session, Purpose purpose, String submittedCode) {
        Object value = session.getAttribute(sessionKey(purpose));
        if (!(value instanceof OtpChallenge challenge)) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        if (!now.isBefore(challenge.createdAt.plus(OTP_TTL))) {
            session.removeAttribute(sessionKey(purpose));
            return Optional.empty();
        }

        if (submittedCode != null && submittedCode.matches("[0-9]{6}")
                && MessageDigest.isEqual(challenge.hash,
                hashOtp(purpose, challenge.email, challenge.salt, submittedCode))) {
            session.removeAttribute(sessionKey(purpose));
            return Optional.of(challenge.email);
        }

        challenge.failedAttempts++;
        if (challenge.failedAttempts >= MAX_VERIFICATION_FAILURES) {
            session.removeAttribute(sessionKey(purpose));
        }
        return Optional.empty();
    }

    public void clearOtp(HttpSession session, Purpose purpose) {
        session.removeAttribute(sessionKey(purpose));
    }

    public long secondsUntilResend(String email) {
        SendWindow window = sendWindows.get(normalizeEmail(email));
        if (window == null) {
            return 0;
        }

        synchronized (window) {
            if (window.lastSentAt == null) {
                return 0;
            }
            long millisRemaining = Duration.between(clock.instant(),
                    window.lastSentAt.plus(RESEND_INTERVAL)).toMillis();
            return millisRemaining <= 0 ? 0 : (millisRemaining + 999) / 1000;
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void sendEmail(Purpose purpose, String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (mailFrom != null && !mailFrom.isBlank()) {
            message.setFrom(mailFrom);
        }
        message.setTo(email);
        message.setSubject("Mã xác nhận Lapzone");
        message.setText("Mã xác nhận của bạn là: " + code
                + "\nMã có hiệu lực trong 10 phút. Nếu không phải bạn thì bỏ qua email này.");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            LOGGER.error("Unable to send {} OTP email; exception type: {}",
                    purpose, ex.getClass().getName());
            throw new OtpException("Không thể gửi email xác nhận lúc này. Vui lòng thử lại sau.");
        }
    }

    private static String sessionKey(Purpose purpose) {
        return OTP_SESSION_PREFIX + purpose.name();
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    private static byte[] hashOtp(Purpose purpose, String email, byte[] salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(purpose.name().getBytes(StandardCharsets.UTF_8));
            digest.update(email.getBytes(StandardCharsets.UTF_8));
            return digest.digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static final class OtpException extends RuntimeException {
        public OtpException(String message) {
            super(message);
        }
    }

    private static final class OtpChallenge implements Serializable {
        private final String email;
        private final byte[] salt;
        private final byte[] hash;
        private final Instant createdAt;
        private int failedAttempts;

        private OtpChallenge(String email, byte[] salt, byte[] hash, Instant createdAt) {
            this.email = email;
            this.salt = salt;
            this.hash = hash;
            this.createdAt = createdAt;
        }

    }

    private static final class SendWindow {
        private final ArrayDeque<Instant> sentAt = new ArrayDeque<>();
        private Instant lastSentAt;

        private void prune(Instant now) {
            Instant cutoff = now.minus(Duration.ofHours(1));
            while (!sentAt.isEmpty() && !sentAt.peekFirst().isAfter(cutoff)) {
                sentAt.removeFirst();
            }
        }
    }
}
