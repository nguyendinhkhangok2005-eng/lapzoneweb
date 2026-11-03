package com.lapzone.lapzoneweb.model.validation;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*(),.?\":{}|<>]).{8,}$";

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(REGEX);

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }
}
